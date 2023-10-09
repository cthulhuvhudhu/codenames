package codenames

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.mongodb.MongoTimeoutException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest
internal class CodenamesControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    private lateinit var codenamesService: CodenamesService

    @BeforeEach
    fun setup() {
        // You can initialize any mock data or configurations here before each test if needed.
        every { codenamesService.getGame("game-dne") } returns null
    }

    @Test
    fun createNewGame() {
        val gameId = "game-123"
        every { codenamesService.createNewGame() } returns gameId

        mockMvc.perform(post("/api/games/new"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/plain;charset=UTF-8")))
            .andExpect(content().string(gameId))
    }

    @Test
    fun startGame() {
        val gameId = "game-123"
        val expectedGameData = GameData(gameId)
        every { codenamesService.startGame(gameId) } returns expectedGameData

        mockMvc.perform(post("/api/games/start?gameId=$gameId"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(expectedGameData.id))

        every { codenamesService.startGame("game-dne") } throws NullPointerException()

        mockMvc.perform(post("/api/games/start?gameId=game-dne"))
            .andExpect(status().is4xxClientError)
            .andExpect(content().string("Bad request"))
    }

    @Test
    fun `test getting game data by ID`() {
        // Mocking the service behavior
        val gameId = "game-123"
        val expectedGameData = GameData(gameId)
        every { codenamesService.getGame(gameId) } returns expectedGameData

        // Performing the GET request
        mockMvc.perform(get("/api/games?gameId=$gameId"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(expectedGameData.id))

        every { codenamesService.getGame("game-dne") } throws java.util.NoSuchElementException()

        mockMvc.perform(get("/api/games?gameId=game-dne"))
            .andExpect(status().isNotFound)
            .andExpect(content().string("Object not found!"))

        // No ID provided
        mockMvc.perform(get("/api/games"))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun `test repo error`() {
        every { codenamesService.getGames() } throws MongoTimeoutException("")
        mockMvc.perform(get("/api/games/all"))
            .andExpect(status().is5xxServerError)
            .andExpect(content().string("Internal server error"))
    }

    @Test
    fun giveClue() {
        val gameId = "game123"
        val clue = Clue("clue", 1)
        every { codenamesService.giveClue(gameId, clue) } returns GameData()

        val mapper = ObjectMapper()
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false)

        val requestJson = mapper.writer().writeValueAsString(clue)
        mockMvc.perform(post("/api/games/clue?gameId=$gameId").contentType(
            MediaType(MediaType.APPLICATION_JSON)
        ).content(requestJson))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun makeGuess() {
        val gameId = "game123"
        val guess = "guess"
        every { codenamesService.makeGuess(gameId, guess) } returns GameData()


        mockMvc.perform(post("/api/games/guess?guess=$guess&gameId=$gameId").contentType(
            MediaType(MediaType.APPLICATION_JSON)
        ))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun clearGames() {
        justRun { codenamesService.clear() }
        mockMvc.perform(delete("/api/games").accept(MediaType.ALL))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
    }
}
