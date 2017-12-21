package cool.graph.api.database.import_export

import cool.graph.api.ApiDependencies
import cool.graph.api.database.import_export.ImportExport._
import cool.graph.api.database.{DatabaseMutationBuilder, ProjectRelayId, ProjectRelayIdTable}
import cool.graph.cuid.Cuid
import cool.graph.shared.models.{Model, Project, Relation, RelationSide}
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery
import spray.json._
import MyJsonProtocol._

import scala.concurrent.Future
import scala.util.Try


class BulkImport(project: Project)(implicit apiDependencies: ApiDependencies){

  val db = apiDependencies.databases

  def executeImport(json: JsValue): Future[JsValue] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val bundle = json.convertTo[ImportBundle]
    val count  = bundle.values.elements.length

    val actions = bundle.valueType match {
      case "nodes"     => generateImportNodesDBActions( bundle.values.elements.map(convertToImportNode))
      case "relations" => generateImportRelationsDBActions( bundle.values.elements.map(convertToImportRelation))
      case "lists"     => generateImportListsDBActions(bundle.values.elements.map(convertToImportList))
    }

    val res: Future[Vector[Try[Int]]] = runDBActions(actions)

    def messageWithOutConnection(tryelem: Try[Any]): String = tryelem.failed.get.getMessage.substring(tryelem.failed.get.getMessage.indexOf(")") + 1)
    res
      .map(vector =>
        vector.zipWithIndex.collect {
          case (elem, idx) if elem.isFailure && idx < count  => Map("index" -> idx, "message"           -> messageWithOutConnection(elem)).toJson
          case (elem, idx) if elem.isFailure && idx >= count => Map("index" -> (idx - count), "message" -> messageWithOutConnection(elem)).toJson
      })
      .map(x => JsArray(x))
  }

  private def getImportIdentifier(map: Map[String, Any]): ImportIdentifier =
    ImportIdentifier(map("_typeName").asInstanceOf[String], map("id").asInstanceOf[String])

  private def convertToImportNode(json: JsValue): ImportNode = {
    val map      = json.convertTo[Map[String, Any]]
    val valueMap = map.collect { case (k, v) if k != "_typeName" && k != "id" => (k, v) }

    ImportNode(getImportIdentifier(map), valueMap)
  }

  private def convertToImportList(json: JsValue): ImportList = {
    val map      = json.convertTo[Map[String, Any]]
    val valueMap = map.collect { case (k, v) if k != "_typeName" && k != "id" => (k, v.asInstanceOf[List[Any]].toVector) }

    ImportList(getImportIdentifier(map), valueMap)
  }

  private def convertToImportRelation(json: JsValue): ImportRelation = {
    val array    = json.convertTo[JsArray]
    val leftMap  = array.elements.head.convertTo[Map[String, String]]
    val rightMap = array.elements.reverse.head.convertTo[Map[String, String]]
    val left     = ImportRelationSide(getImportIdentifier(leftMap), leftMap("fieldName"))
    val right    = ImportRelationSide(getImportIdentifier(rightMap), rightMap("fieldName"))

    ImportRelation(left, right)
  }

  private def generateImportNodesDBActions(nodes: Vector[ImportNode]): DBIOAction[Vector[Try[Int]], NoStream, Effect.Write] = {
    val items = nodes.map { element =>
      val id                              = element.identifier.id
      val model                           = project.getModelByName_!(element.identifier.typeName)
      val listFields: Map[String, String] = model.scalarListFields.map(field => field.name -> "[]").toMap
      val values: Map[String, Any]        = element.values ++ listFields + ("id" -> id)

      DatabaseMutationBuilder.createDataItem(project.id, model.name, values).asTry
    }

    val relayIds: TableQuery[ProjectRelayIdTable] = TableQuery(new ProjectRelayIdTable(_, project.id))
    val relay = nodes.map { element =>
      val id    = element.identifier.id
      val model = project.getModelByName_!(element.identifier.typeName)
      val x     = relayIds += ProjectRelayId(id = id, model.id)
      x.asTry
    }
    DBIO.sequence(items ++ relay)
  }

  private def generateImportRelationsDBActions(relations: Vector[ImportRelation]): DBIOAction[Vector[Try[Int]], NoStream, Effect.Write] = {
    val x = relations.map { element =>
      val fromModel                                                 = project.getModelByName_!(element.left.identifier.typeName)
      val fromField                                                 = fromModel.getFieldByName_!(element.left.fieldName)
      val relationSide: cool.graph.shared.models.RelationSide.Value = fromField.relationSide.get
      val relation: Relation                                        = fromField.relation.get

      val aValue: String = if (relationSide == RelationSide.A) element.left.identifier.id else element.right.identifier.id
      val bValue: String = if (relationSide == RelationSide.A) element.right.identifier.id else element.left.identifier.id

      val aModel: Model = relation.getModelA_!(project)
      val bModel: Model = relation.getModelB_!(project)

//      def getFieldMirrors(model: Model, id: String) =
//        relation.fieldMirrors
//          .filter(mirror => model.fields.map(_.id).contains(mirror.fieldId))
//          .map(mirror => {
//            val field = project.getFieldById_!(mirror.fieldId)
//            MirrorFieldDbValues(
//              relationColumnName = RelationFieldMirrorColumn.mirrorColumnName(project, field, relation),
//              modelColumnName = field.name,
//              model.name,
//              id
//            )
//          })
//
//      val fieldMirrors: List[MirrorFieldDbValues] = getFieldMirrors(aModel, aValue) ++ getFieldMirrors(bModel, bValue)

      DatabaseMutationBuilder.createRelationRow(project.id, relation.id, Cuid.createCuid(), aValue, bValue, List.empty).asTry  // the empty list is for the RelationFieldMirrors
    }
    DBIO.sequence(x)
  }

  private def generateImportListsDBActions(lists: Vector[ImportList]): DBIOAction[Vector[Try[Int]], NoStream, Effect.Write] = {
    val updateListValueActions = lists.map { element =>
      DatabaseMutationBuilder.updateDataItemListValue(project.id, element.identifier.typeName, element.identifier.id, element.values).asTry
    }
    DBIO.sequence(updateListValueActions)
  }

  private def runDBActions(actions: DBIOAction[Vector[Try[Int]], NoStream, Effect.Write]): Future[Vector[Try[Int]]] = db.master.run(actions)
}