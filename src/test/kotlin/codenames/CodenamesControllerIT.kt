package codenames

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.util.*

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@Import(CodenamesService::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
internal class CodenamesControllerIT {

    @Autowired
    private lateinit var gameDataRepository: GameDataRepository

    @Autowired
    private lateinit var codenamesService: CodenamesService

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    val uriRoot = "/api/games"

    companion object {
        @Container
        val mongoDBContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:latest"))
            .withExposedPorts(27017)

        @JvmStatic
        @DynamicPropertySource
        fun mongoDbProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl)
        }

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            mongoDBContainer.start()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            mongoDBContainer.stop()
        }
    }

    @Test
    fun `test NEW game`() {
        //  create new game; Returns valid UUID gameId and game state
        val strResp = restTemplate.exchange("$uriRoot/new", HttpMethod.POST, null, String::class.java)
        assert(strResp.statusCode == HttpStatus.OK) { "response was ${strResp.statusCode} expected 200" }
        assertThat(strResp.body).isNotEmpty
        val gameId = strResp.body!!
        assertDoesNotThrow { UUID.fromString(gameId) }
        assert(gameDataRepository.existsById(gameId))
        val gameData = gameDataRepository.findById(gameId).get()
        assert(gameData.gameStatus == GameStatus.NEW)  { "Game status expected to be NEW, was ${gameData.gameStatus}"}
        assert(gameData.startingTeam == null) { "New game shouldn't have a starting team yet" }
        assert(gameData.currentTeam == null) { "New game shouldn't have a current team yet" }
        assert(gameData.board.isEmpty()) { "New game shouldn't have a board yet" }
        assert(gameData.winner == null) { "New game shouldn't have a winner yet" }
        assert(gameData.turns.isEmpty()) { "New game shouldn't have any turns yet" }
        assert(gameData.redAgentsLeft == codenamesService.baseNumAgents && gameData.blueAgentsLeft == codenamesService.baseNumAgents) { "New game should have both teams with the base number of agents left" }
        assert(gameData.createdAt < LocalDateTime.now()) { "Game should have set and valid creation date" }
        assert(gameData.createdAt <= gameData.updatedAt) { "Game should have set and valid modified date" }
    }

    @Test
    fun `test ERROR invalid id START game`() {
        var fakeUUID = UUID.randomUUID()
        while ( gameDataRepository.existsById(fakeUUID.toString())) { fakeUUID = UUID.randomUUID() }

        val resp = restTemplate.exchange("$uriRoot/start?gameId=$fakeUUID", HttpMethod.POST, null, String::class.java)
        assert(resp.statusCode == HttpStatus.NOT_FOUND) { "response was ${resp.statusCode} expected 404" }
    }

    @Test
    fun `test GET game`() {
        val gameId = createNewGame()
        val resp = restTemplate.getForEntity("$uriRoot?gameId=$gameId", GameData::class.java)
        assertThat(resp.body).isNotNull
    }

    @Test
    fun `test ERROR invalid GET game`() {
        var fakeUUID = UUID.randomUUID()
        while ( gameDataRepository.existsById(fakeUUID.toString())) { fakeUUID = UUID.randomUUID() }

        val resp = restTemplate.exchange("$uriRoot/start?gameId=$fakeUUID", HttpMethod.POST, null, String::class.java)
        assert(resp.statusCode == HttpStatus.NOT_FOUND) { "response was ${resp.statusCode} expected 404" }
    }

    @Test
    fun `test GET ALL games empty`() {
        gameDataRepository.deleteAll()
        val resp = restTemplate.exchange("$uriRoot/all", HttpMethod.GET, null, typeReference<List<GameData>>())
        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assert(resp.body!!.isEmpty())
    }

    @Test
    fun `test GET ALL games`() {
        gameDataRepository.deleteAll()
        createNewGame()
        createNewGame()
        createNewGame()
        val resp = restTemplate.exchange("$uriRoot/all", HttpMethod.GET, null, typeReference<List<GameData>>())
        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assert(resp.body!!.size == 3)
    }

    @Test
    fun `test DELETE`() {
        createNewGame()
        createNewGame()
        createNewGame()
        var resp = restTemplate.exchange(uriRoot, HttpMethod.DELETE, null, typeReference<Any>())
        assert(resp.statusCode == HttpStatus.NO_CONTENT) { "response was ${resp.statusCode} expected 204" }
        assertNull(resp.body)
        assert(gameDataRepository.findAll().isEmpty())

        resp = restTemplate.exchange(uriRoot, HttpMethod.DELETE, null, typeReference<Any>())
        assert(resp.statusCode == HttpStatus.NO_CONTENT) { "response was ${resp.statusCode} expected 204" }
        assertNull(resp.body)
        assert(gameDataRepository.findAll().isEmpty())
    }

    @Test
    fun `test START game`() {
        // Setup game (new)
        val gameId = createNewGame()

        // start game; updated GameStatus, agentsLeft, board initialized, teams
        val resp = restTemplate.exchange("$uriRoot/start?gameId=$gameId", HttpMethod.POST, null, GameData::class.java)
        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        val gameData = resp.body!!
        assertDataProperties(gameData)
        assert(gameData.startingTeam == Team.BLUE || gameData.startingTeam == Team.RED) { "Starting team expected RED or BLUE, was ${gameData.startingTeam}"}
        val startTeam = gameData.startingTeam
        val currentTeam = gameData.currentTeam
        val groupedWordList = gameData.board.groupBy { it.team }.mapValues { it.value.map { card ->  card.word } }

        if (startTeam == Team.RED) {
            assert(gameData.redAgentsLeft == gameData.blueAgentsLeft+1) { "Expected starting team to have one more agent to find." }
        } else {
            assert(gameData.blueAgentsLeft == gameData.redAgentsLeft+1) { "Expected starting team to have one more agent to find." }
        }
        assert(groupedWordList[Team.BLUE]!!.size == gameData.blueAgentsLeft) {  "Board list has ${groupedWordList[Team.BLUE]!!.size} cards for BLUE, but ${gameData.blueAgentsLeft} agents left" }
        assert(groupedWordList[Team.RED]!!.size == gameData.redAgentsLeft) { "Board list has ${groupedWordList[Team.RED]!!.size} cards for RED, but ${gameData.redAgentsLeft} agents left" }
        assert(gameData.gameStatus == GameStatus.IN_PROGRESS) { "Expected game status to be IN_PROGRESS, was ${gameData.gameStatus}" }
        assert(currentTeam == startTeam) { "Expected current team to match starting team, instead was $currentTeam" }
        assert(groupedWordList[Team.ASSASSIN]!!.size == 1)
        assert(groupedWordList[Team.CITIZEN]!!.size == gameData.board.size - (1 + gameData.redAgentsLeft + gameData.blueAgentsLeft))
        assert(gameData.winner == null) { "Started game shouldn't have a winner yet" }
        assert(gameData.turns.isEmpty()) { "Started game shouldn't have any turns yet" }
    }

    @Test
    fun `test ERROR with CLUE on NEW game`() {
        // Setup: NEW game
        val gameId = createNewGame()

        val clueReq = HttpEntity(Clue("clueString", 2))
        val strResp = restTemplate.exchange("$uriRoot/clue?gameId=$gameId", HttpMethod.POST, clueReq, String::class.java)
        assert(strResp.statusCode == HttpStatus.BAD_REQUEST) { "response was ${strResp.statusCode}, expected 400" }

    }

    @Test
    fun `test ERROR with GUESS on NEW game`() {
        // Setup: NEW game
        val gameId = createNewGame()
        val strResp = restTemplate.exchange("$uriRoot/guess?guess=asdf&gameId=$gameId", HttpMethod.POST, null, String::class.java)
        assert(strResp.statusCode == HttpStatus.BAD_REQUEST) { "response was ${strResp.statusCode}, expected 400" }

    }

    @Test
    fun `test ERROR with valid GUESS on STARTED game`() {
        // Setup: START game
        val gameData = startGame()
        val guess = gameData.board.first().word
        val strResp = restTemplate.exchange("$uriRoot/guess?guess=$guess&gameId=${gameData.id}", HttpMethod.POST, null, String::class.java)
        assert(strResp.statusCode == HttpStatus.BAD_REQUEST) { "response was ${strResp.statusCode}, expected 400" }
    }

    @Test
    fun `test illegal CLUE changes TURN`() {
        val gameData = startGame()
        val clue = Clue(gameData.board.first().word, 1)

        val clueReq = HttpEntity(clue)
        val resp = restTemplate.exchange("$uriRoot/clue?gameId=${gameData.id}", HttpMethod.POST, clueReq, GameData::class.java)

        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assertDataProperties(resp.body!!)
        assert(resp.body!!.gameStatus == GameStatus.IN_PROGRESS)
        assert(resp.body!!.turns.size == 1)
        assertTurn(resp.body!!.turns.last(), clue, gameData.currentTeam!!, 0)
        assert(resp.body!!.currentTeam == gameData.currentTeam!!.getOpponent()) { "Expected to end turn since clue was invalid" }
    }

    @Test
    fun `test ERROR invalid GUESS before CLUE`() {
        val gameId = startGame().id
        val strResp = restTemplate.exchange("$uriRoot/guess?guess=goodGuess&gameId=$gameId", HttpMethod.POST, null, String::class.java)
        assert(strResp.statusCode == HttpStatus.BAD_REQUEST) { "response was ${strResp.statusCode}, expected 400" }
    }

    @Test
    fun `test good CLUE adds TURN`() {
        val gameData = startGame()
        val clue = Clue("clue", 1)

        val clueReq = HttpEntity(clue)
        val resp = restTemplate.exchange("$uriRoot/clue?gameId=${gameData.id}", HttpMethod.POST, clueReq, GameData::class.java)

        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assertDataProperties(resp.body!!)
        assert(resp.body!!.gameStatus == GameStatus.IN_PROGRESS)
        assert(resp.body!!.turns.size == 1)
        assertTurn(resp.body!!.turns.last(), clue, gameData.currentTeam!!, 2)
        assert(resp.body!!.currentTeam == gameData.currentTeam!!) { "Good clue means team gets to guess" }
    }

    @Test
    fun `test INCORRECT assassin GUESS to LOSE`() {
        val gameData = startGame()
        val clue = giveClue(gameData)
        val guess = gameData.board.first { it.team == Team.ASSASSIN }
        val resp = restTemplate.exchange("$uriRoot/guess?guess=${guess.word}&gameId=${gameData.id}", HttpMethod.POST, null, GameData::class.java)

        assertThat(resp.body).isNotNull
        assertDataProperties(resp.body!!)
        assert(resp.body!!.gameStatus == GameStatus.GAME_OVER)
        assert(resp.body!!.turns.size == 2)
        assertTurn(resp.body!!.turns.last(), clue, gameData.currentTeam!!, 0, guess.word, false)
        assert(resp.body!!.winner == gameData.currentTeam!!.getOpponent())
    }

    @Test
    fun `test INCORRECT citizen GUESS changes TURN`() {
        val gameData = startGame()
        val clue = giveClue(gameData)
        val guess = gameData.board.first { it.team == Team.CITIZEN }
        val turnsIndex = 2
        val resp = restTemplate.exchange("$uriRoot/guess?guess=${guess.word}&gameId=${gameData.id}", HttpMethod.POST, null, GameData::class.java)

        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assertDataProperties(resp.body!!)
        assert(resp.body!!.gameStatus == GameStatus.IN_PROGRESS)
        assert(resp.body!!.turns.size == turnsIndex)
        assertTurn(resp.body!!.turns.last(), clue, gameData.currentTeam!!, 0, guess.word, false)
        assert(resp.body!!.currentTeam == gameData.currentTeam!!.getOpponent()) { "Bad guess means opponent team gets to guess now" }
        assert(resp.body!!.board.first { it.word == guess.word }.isVisible) { "Guessed CITIZEN should be visible" }
    }

    @Test
    fun `test INCORRECT opponent GUESS changes TURN`() {
        val gameData = startGame()
        val clue = giveClue(gameData)
        val guess = gameData.board.first { it.team == gameData.currentTeam!!.getOpponent() }
        val turnsIndex = 2
        val resp = restTemplate.exchange("$uriRoot/guess?guess=${guess.word}&gameId=${gameData.id}", HttpMethod.POST, null, GameData::class.java)

        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assert(resp.body!!.gameStatus == GameStatus.IN_PROGRESS)
        assert(resp.body!!.turns.size == turnsIndex)
        assertTurn(resp.body!!.turns.last(), clue, gameData.currentTeam!!, 0, guess.word, false)
        assert(resp.body!!.currentTeam == gameData.currentTeam!!.getOpponent()) { "Bad guess means opponent team gets to guess now" }

        if (gameData.currentTeam!! == Team.RED) {
            assert(resp.body!!.blueAgentsLeft == codenamesService.baseNumAgents-1) { "Guessing opponent agent should reduce count" }
        } else {
            assert(resp.body!!.redAgentsLeft == codenamesService.baseNumAgents-1) { "Guessing opponent agent should reduce count" }
        }
        assert(resp.body!!.board.first { it.word == guess.word }.isVisible) { "Guessed opponent card should be visible" }
    }

    @Test
    fun `test correct GUESS adds TURN`() {
        val gameData = startGame()
        val clue = giveClue(gameData)
        val guess = gameData.board.first { it.team == gameData.currentTeam!! }
        val turnsIndex = 2
        val resp = restTemplate.exchange("$uriRoot/guess?guess=${guess.word}&gameId=${gameData.id}", HttpMethod.POST, null, GameData::class.java)

        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode} expected 200" }
        assertThat(resp.body).isNotNull
        assertDataProperties(resp.body!!)
        assert(resp.body!!.gameStatus == GameStatus.IN_PROGRESS)
        assert(resp.body!!.turns.size == turnsIndex)
        assertTurn(resp.body!!.turns.last(), clue, gameData.currentTeam!!, 1, guess.word, true)
        assert(resp.body!!.currentTeam == gameData.currentTeam!!) { "Good guess means same team gets to keep guessing" }

        if (gameData.currentTeam!! == Team.RED) {
            assert(resp.body!!.redAgentsLeft == gameData.redAgentsLeft-1) { "Guessing agent should reduce count" }
            assert(resp.body!!.blueAgentsLeft == gameData.blueAgentsLeft) { "Guessing agent shouldn't reduce opponent count" }
        } else {
            assert(resp.body!!.blueAgentsLeft == gameData.blueAgentsLeft-1) { "Guessing agent should reduce count" }
            assert(resp.body!!.redAgentsLeft == gameData.redAgentsLeft) { "Guessing agent shouldn't reduce opponent count" }
        }
        assert(resp.body!!.board.first { it.word == guess.word }.isVisible) { "Guessed team card should be visible" }
    }

    @Test
    fun `test ERROR visible GUESS`() {
        // Setup: Game with one card already revealed
        val gameData = startGame()
        giveClue(gameData, 2)
        val guess = makeGuess(gameData).turns.last().guessString

        val strResp = restTemplate.exchange("$uriRoot/guess?guess=$guess&gameId=${gameData.id}", HttpMethod.POST, null, String::class.java)

        assert(strResp.statusCode == HttpStatus.BAD_REQUEST) { "response was ${strResp.statusCode} expected 400" }
    }

    @Test
    fun `test correct GUESS advances TURN`() {
        val gameData = startGame()
        val clue = giveClue(gameData)

        var gameDataNext = makeGuess(gameData)
        assertDataProperties(gameDataNext)
        assert(gameDataNext.gameStatus == GameStatus.IN_PROGRESS)
        assert(gameDataNext.turns.size == 2)
        assertTurn(gameDataNext.turns.last(), clue, gameData.currentTeam!!, 1, gameDataNext.turns.last().guessString, true)
        assert(gameData.currentTeam == gameDataNext.currentTeam) { "Guess was correct, same team should be able to guess again" }
        assert(gameDataNext.board.first { it.word == gameDataNext.turns.last().guessString }.isVisible) { "Guessed agent should be visible" }

        gameDataNext = makeGuess(gameDataNext)
        assertDataProperties(gameDataNext)
        assert(gameDataNext.gameStatus == GameStatus.IN_PROGRESS)
        assert(gameDataNext.turns.size == 3)
        assertTurn(gameDataNext.turns.last(), clue, gameData.currentTeam!!, 0, gameDataNext.turns.last().guessString, true)
        assert(gameDataNext.board.first { it.word == gameDataNext.turns.last().guessString }.isVisible) { "Guessed agent should be visible" }
        assert(gameData.currentTeam!!.getOpponent() == gameDataNext.currentTeam)  { "Guess was correct but out of guesses, next team's turn" }

        if (gameData.currentTeam!! == Team.RED) {
            assert(gameDataNext.redAgentsLeft == gameData.redAgentsLeft-2) { "Guessing agent should reduce count" }
            assert(gameDataNext.blueAgentsLeft == gameData.blueAgentsLeft) { "Guessing agent shouldn't reduce opponent count" }
        } else {
            assert(gameDataNext.blueAgentsLeft == gameData.blueAgentsLeft-2) { "Guessing agent should reduce count" }
            assert(gameDataNext.redAgentsLeft == gameData.redAgentsLeft) { "Guessing agent shouldn't reduce opponent count" }
        }
    }

    @Test
    fun `test correct GUESS to WIN`() {
        // Setup: Start game, pull board words
        var gameData = startGame()
        val gameId = gameData.id
        val currentTeam = gameData.currentTeam!!

        // Advance game state; give good clue
        var clueNum = 10
        val clue = giveClue(gameData, clueNum)
        var turnsIndex = 1

        for (guess in gameData.board.filter { !it.isVisible && it.team == gameData.currentTeam!! }.map { it.word } ) {
            println("Guessing $guess")
            clueNum--
            turnsIndex++
            val resp = restTemplate.exchange("$uriRoot/guess?guess=$guess&gameId=$gameId", HttpMethod.POST, null, GameData::class.java)
            assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode}" }
            assertThat(resp.body).isNotNull
            assertDataProperties(resp.body!!)
            assert(resp.body!!.turns.size == turnsIndex)
            assertTurn(resp.body!!.turns.last(), clue, currentTeam, clueNum+1, guess, true)
            assert(resp.body!!.currentTeam == currentTeam) { "Good guess means team gets to continue guessing" }
            gameData = resp.body!!
        }

        assert(gameData.gameStatus == GameStatus.GAME_OVER)
        assert(gameData.winner == currentTeam)
        if (currentTeam == Team.RED) {
            assert(gameData.redAgentsLeft == 0)
            assert(gameData.blueAgentsLeft == codenamesService.baseNumAgents)
        } else {
            assert(gameData.blueAgentsLeft == 0)
            assert(gameData.redAgentsLeft == codenamesService.baseNumAgents)
        }
    }

    @Test
    fun `test INCORRECT opponent GUESS to LOSE`() {
        // Setup: Start game, pull board words
        var gameData = startGame()
        val gameId = gameData.id
        var currentTeam = gameData.currentTeam!!

        // Advance game state; give good clue
        var clueNum = codenamesService.baseNumAgents-1
        var clue = giveClue(gameData, clueNum)
        var turnsIndex = 1

        // team 1 = guess baseNum times, change turn
        val guessList = gameData.board.filter { !it.isVisible && it.team == gameData.currentTeam!! }.map { it.word }.toMutableList()

        while (guessList.size > 1) {
            val guess = guessList.removeFirst()
            println("Guessing $guess")
            clueNum--
            turnsIndex++
            val resp = restTemplate.exchange("$uriRoot/guess?guess=$guess&gameId=$gameId", HttpMethod.POST, null, GameData::class.java)
            assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode}" }
            assertThat(resp.body).isNotNull
            assertDataProperties(resp.body!!)
            assert(resp.body!!.turns.size == turnsIndex)
            assertTurn(resp.body!!.turns.last(), clue, currentTeam, clueNum+1, guess, true)
            if (clueNum < 0) {
                assert(resp.body!!.currentTeam == currentTeam.getOpponent()) { "Good guess but out of turns means it's opponent's turn" }
                gameData = resp.body!!
                currentTeam = gameData.currentTeam!!
            } else {
                assert(resp.body!!.currentTeam == currentTeam) { "Good guess means team gets to continue guessing" }
            }
        }
        assert(gameData.gameStatus == GameStatus.IN_PROGRESS)
        if (currentTeam == Team.BLUE) {
            assert(gameData.redAgentsLeft == 1)
            assert(gameData.blueAgentsLeft == codenamesService.baseNumAgents)
        } else {
            assert(gameData.blueAgentsLeft == 1)
            assert(gameData.redAgentsLeft == codenamesService.baseNumAgents)
        }

        // Make good clue
        clue = giveClue(gameData)
        turnsIndex += 2
        val guess = guessList.removeFirst()
        val resp = restTemplate.exchange("$uriRoot/guess?guess=$guess&gameId=$gameId", HttpMethod.POST, null, GameData::class.java)
        assert(resp.statusCode == HttpStatus.OK) { "response was ${resp.statusCode}" }
        assertThat(resp.body).isNotNull
        assertDataProperties(resp.body!!)
        assert(resp.body!!.gameStatus == GameStatus.GAME_OVER)
        assert(resp.body!!.turns.size == turnsIndex)
        assertTurn(resp.body!!.turns.last(), clue, currentTeam, 0, guess, false)
        assert(resp.body!!.currentTeam!! == currentTeam)

        if (currentTeam == Team.BLUE) {
            assert(resp.body!!.redAgentsLeft == 0)
            assert(resp.body!!.blueAgentsLeft == codenamesService.baseNumAgents)
        } else {
            assert(resp.body!!.blueAgentsLeft == 0)
            assert(resp.body!!.redAgentsLeft == codenamesService.baseNumAgents)
        }
    }

    private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}

    private fun createNewGame(): String {
        return restTemplate.exchange("$uriRoot/new", HttpMethod.POST, null, String::class.java).body!!
    }

    private fun startGame(): GameData {
        val gameId = createNewGame()
        return restTemplate.exchange("$uriRoot/start?gameId=$gameId", HttpMethod.POST, null, GameData::class.java).body!!
    }

    private fun giveClue(gameData: GameData, clueNum: Int = 1): Clue {
        val clue = Clue("clue", clueNum)

        val clueReq = HttpEntity(clue)
        restTemplate.exchange("$uriRoot/clue?gameId=${gameData.id}", HttpMethod.POST, clueReq, GameData::class.java)
        return clue
    }

    private fun makeGuess(gameData: GameData, team: Team? = gameData.currentTeam): GameData {
        var guess = "asdf"
        if (team != null ) {
            guess = gameData.board.first { it.team == gameData.currentTeam!! && !it.isVisible }.word
        }
        return restTemplate.exchange("$uriRoot/guess?guess=${guess}&gameId=${gameData.id}", HttpMethod.POST, null, GameData::class.java).body!!
    }

    private fun assertDataProperties(d: GameData) {
        assertDoesNotThrow { UUID.fromString(d.id) }
        assert(d.createdAt < LocalDateTime.now()) { "Game should have set and valid creation date" }
        assert(d.createdAt < d.updatedAt) { "Game should have set and valid modified date" }
    }

    private fun assertTurn(turn: Turn, clue: Clue, team: Team, rem: Int?, guess: String? = null, correct: Boolean? = null) {
        assert(turn.correct == correct) { "Incorrect correct: turn: ${turn.correct} expected: $correct" }
        assert(turn.clueNum == clue.clueNum) { "Incorrect clueNum: turn: ${turn.clueNum} expected: $clue.clueNum" }
        assert(turn.clueString == clue.clueString) { "Incorrect clueStr: turn: ${turn.clueString} expected: $clue.clueString" }
        assert(turn.guessString == guess)  { "Incorrect guessStr: turn: ${turn.guessString} expected: $guess" }
        assert(turn.guessesLeft == rem) { "Incorrect guessNum: turn: ${turn.guessesLeft} expected: $rem" }
        assert(turn.team == team) { "Incorrect team: turn: ${turn.team} expected: $team"}
    }
}
