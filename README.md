# codenames

A kotlin spring API to support a digital version of the card game Codenames.
See [here](https://www.ultraboardgames.com/codenames/game-rules.php) for rules and description.

## Setup and run using local mongodb for testing
1. Setup network `docker network create codenames-net`
2. Run mongo container ```docker run -d -p 27017:27017 --name codenames-db --network codenames-net \
   -v mongo-data:/data/db mongo:latest -e MONGO_INITDB_ROOT_USERNAME=admin -e MONGO_INITDB_ROOT_PASSWORD=password```
3. if this is the first time, access the container and create the database:
> `docker exec -it codenames-db /bin/bash` \
`mongo` \
`use codenames`

## To Run the Application

1. Execute `gradle clean build` in the project root directory.
2. Build docker image: `docker build -t codenames .`
3. Run docker `docker run -p 8080:8080 --network codenames-net codenames` to use local mongodb
4. Alternatively, you may substitute a connections string for Atlas in a local.properties file.
