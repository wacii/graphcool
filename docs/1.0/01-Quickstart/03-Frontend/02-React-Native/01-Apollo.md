---
alias: yuwocai8ta
description: Get started in 5 min with React Native, GraphQL and Apollo Client by building a simple Instagram clone.
github: https://github.com/graphql-boilerplates/react-native-graphql/tree/master/basic
---

# React Native & Apollo Quickstart

In this quickstart tutorial, you'll learn how to build a fullstack app with React Native, GraphQL and Node.js. You will use [`graphql-yoga`](https://github.com/graphcool/graphql-yoga/) as your web server which is connected to a "GraphQL database" using [`graphcool-binding`](https://github.com/graphcool/graphcool-binding).

> The code for this project can be found as a _GraphQL boilerplate_ project on [GitHub](https://github.com/graphql-boilerplates/react-native-graphql/tree/master/basic).

## Step 1: Install required command line tools

The first thing you need to do is install the command line tools you'll need for this tutorial:

- `graphql-cli` is used initially to bootstrap the file structure for your fullstack app with `graphql create`
- `graphcool` is used continuously to manage your Graphcool database service

<Instruction>

```sh
npm install -g graphql-cli
npm install -g graphcool@beta
```

</Instruction>

## Step 2: Bootstrap your React Native fullstack app

<Instruction>

Now you can use `graphql create` to bootstrap your project. With the following command, you name your project `my-app` and choose to use the `react-native-basic` boilerplate:

```sh
graphql create my-app --boilerplate react-native-basic
cd my-app
```

Feel free to get familiar with the code. The app contains the following React [`components`](https://github.com/graphql-boilerplates/react-native-graphql/tree/master/basic/src/components):

- `Post`: Renders a single post item
- `ListPage`: Renders a list of post items
- `CreatePage`: Allows to create a new post item

Let's also quickly understand the file structure for your GraphQL server:

```
server
├── database
│   ├── datamodel.graphql
│   └── schema.generated.graphql
├── graphcool.yml
├── package.json
├── src
│   ├── index.js
│   └── schema.graphql
└── yarn.lock
```

Here is an explanation of the generated files and their roles in your server-side setup:

- `/server`
  - [`.graphqlconfig.yml`](https://github.com/graphql-boilerplates/node-graphql-server/tree/master/basic/.graphqlconfig.yml) GraphQL configuration file containing the endpoints and schema configuration. Used by the [`graphql-cli`](https://github.com/graphcool/graphql-cli) and the [GraphQL Playground](https://github.com/graphcool/graphql-playground). See [`graphql-config`](https://github.com/graphcool/graphql-config) for more information.
  - [`graphcool.yml`](https://github.com/graphql-boilerplates/node-graphql-server/tree/master/basic/graphcool.yml): The root configuration file for your database service ([documentation](https://www.graph.cool/docs/1.0/reference/graphcool.yml/overview-and-example-foatho8aip)).
- `/server/database`
  - [`database/datamodel.graphql`](https://github.com/graphql-boilerplates/node-graphql-server/tree/master/basic/database/datamodel.graphql) contains the data model that you define for the project (written in [SDL](https://blog.graph.cool/graphql-sdl-schema-definition-language-6755bcb9ce51)). We'll discuss this next.
  - [`database/schema.generated.graphql`](https://github.com/graphql-boilerplates/node-graphql-server/tree/master/basic/database/schema.generated.graphql) defines the **database schema**. It contains the definition of the CRUD API for the types in your data model and is generated based on your `datamodel.graphql`. **You should never edit this file manually**, but introduce changes only by altering `datamodel.graphql` and run `graphcool deploy`.
- `/server/src`
  - [`/server/src/schema.graphql`](https://github.com/graphql-boilerplates/node-graphql-server/tree/master/basic/src/schema.graphql) defines your **application schema**. It contains the GraphQL API that you want to expose to your client applications.
  - [`/server/src/index.js`](https://github.com/graphql-boilerplates/node-graphql-server/tree/master/basic/src/index.js) is the entry point of your server, pulling everything together and starting the `GraphQLServer` from [`graphql-yoga`](https://github.com/graphcool/graphql-yoga).

Most important for you at this point are `database/datamodel.graphql` and `src/schema.graphql`. `database/datamodel.graphql` is used to define your data model. This data model is the foundation for the API that's defined in `src/schema.graphql` and exposed to your React Native application.

Here is what the data model looks like:

```graphql(path="server/database/datamodel.graphql")
type Post {
  id: ID! @unique
  createdAt: DateTime!
  updatedAt: DateTime!
  description: String!
  imageUrl: String!
}
```

Based on this data model Graphcool generates the **database schema**, a [GraphQL schema](https://blog.graph.cool/graphql-server-basics-the-schema-ac5e2950214e) that defines a CRUD API for the types in your data model. In your case, this is only the `Post` type. The database schema is stored in `database/schema.generated.graphql` and will be updated every time you [`deploy`](!alias-kee1iedaov) changes to your data model.

## Step 3: Deploy the Graphcool database service

Before you can start the server, you first need to make sure your "GraphQL database" is available. You can do so by deploying the correspdonding Graphcool service that's responsible for the database.

In this case, you'll deploy the Graphcool database serviceto the **free development cluster** of Graphcool Cloud. Note that this cluster is not intended for production use, but rather for development and demo purposes.

> Another option would be to deploy it locally with [Docker](https://www.docker.com/). You can follow the [Node.js Quickstart tutorial](!alias-phe8vai1oo) to learn how that works.

<Instruction>

Deploy the database service from the `server` directory of the project:

```bash(path="")
cd server
graphcool deploy
```

</Instruction>

<Instruction>

When prompted which cluster you want to deploy to, choose the `development` cluster from the **Graphcool Cloud** section.

</Instruction>

> **Note**: If you haven't authenticated with the Graphcool CLI before, this command is going to open up a browser window and ask you to login. Your authentication token will be stored in the global [`~/.graphcool`](!alias-zoug8seen4).

You Graphcool database service is now deployed and accessible under [`http://graphcool/my-app/dev`](http://graphcool/my-app/dev).

As you might recognize, the HTTP endpoint for the database service is composed of the following components:

- The **cluster's domain** (specified as the `host` property in `~/.graphcool/config.yml`): `http://localhost:60000/my-app/dev`
- The **name** of the Graphcool service specified in `graphcool.yml`: `my-app`
- The **stage** to which the service is deployed, by default this is calleds: `dev`

Note that the endpoint is referenced in `server/src/index.js`. There, it is used to instantiate `Graphcool` in order to create a binding between the application schema and the database schema:

```js(path="src/index.js"&nocopy)
const server = new GraphQLServer({
  typeDefs,
  resolvers,
  context: req => ({
    ...req,
    db: new Graphcool({
      schemaPath: './database/schema.generated.graphql',
      endpoint: 'http://localhost:60000/api/my-app/dev',
      secret: 'your-graphcool-secret',
    }),
  }),
})
```

You're now set to start the server! 🚀

## Step 4: Start the server

<Instruction>

Execute the `start` script that's define in `server/package.json`:

```bash(path="server")
yarn start
```

</Instruction>

The server is now running on [http://localhost:4000](http://localhost:4000).

## Step 5: Open a GraphQL playground to send queries and mutations

Now that the server is running, you can use a [GraphQL Playground](https://github.com/graphcool/graphql-playground) to interact with it.

<Instruction>

Open a GraphQL Playground by executing the following command:

```bash(path="server")
graphcool playground
```

</Instruction>

Note that the Playground let's you interact with two GraphQL APIs side-by-side:

- `app`: The web server's GraphQL API defined in the **application schema** (from `./server/src/schema.graphql`)
- `database`: The CRUD GraphQL API of the Graphcool database service defined in the **database schema** (from `./server/database/schema.generated.graphql`)

![](https://imgur.com/z7MWZA8.png)

> Note that each Playground comes with auto-generated documentation which displays all GraphQL operations (i.e. queries, mutations as well as subscriptions) you can send to its API. The documentation is located on the rightmost edge of the Playground.

Once the Playground opened, you can send queries and mutations.

<Instruction>

Paste the following mutation into the left pane of the `app` Playground and hit the _Play_-button (or use the keyboard shortcut `CMD+Enter`):

```grahpql
mutation {
  createPost(
    description: "A rare look into the Graphcool office"
    imageUrl: "https://media2.giphy.com/media/xGWD6oKGmkp6E/200_s.gif"
  ) {
    id
  }
}
```

</Instruction>

<Instruction>

To retrieve the `Post` node that was just created, you can send the following query in the `app` Playground:

```graphql
{
  feed {
    description
    imageUrl
  }
}
```

</Instruction>

<!-- TODO: what is this? should remove?
![](https://imgur.com/w95UEi9.gif)
-->

## Step 6: Launch the React Native app

The last thing to do is actually launching the application 🚀

<Instruction>

Install dependencies and run the app:

```sh(path="server")
cd ..
yarn install
yarn start          # open using the Expo app on your phone
# yarn run ios      # open with iOS simulator
# yarn run android  # open with Android emulator
```

</Instruction>