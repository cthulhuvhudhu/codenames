package codenames

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GameDataRepository : MongoRepository<GameData, String> {}
