package codenames

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RestController
@RequestMapping("/api/games")
class CodenamesController(private val codenamesService: CodenamesService) {
    // TODO filter so identity is only visible for isVisible (RBAC?)

    @GetMapping("/new")
    fun createNewGame(): ResponseEntity<String> {
        val gameId = codenamesService.createNewGame()
        return ResponseEntity.ok(gameId)
    }

    @PostMapping("/start")
    fun startGame(@RequestParam gameId: String): ResponseEntity<GameData> {
        val gameData = codenamesService.startGame(gameId)
        return ResponseEntity.ok(gameData)
    }

    @GetMapping("/spy")
    fun getGameSpyview(@RequestParam gameId: String): ResponseEntity<GameData> {
        return ResponseEntity.ok(codenamesService.getGame(gameId))
    }

    @GetMapping
    fun getGame(@RequestParam gameId: String): ResponseEntity<GameData> {
        val gameData = codenamesService.getGame(gameId)
        return ResponseEntity.ok(gameData)
    }

    @PostMapping("/clue")
    fun giveClue(
        @RequestParam gameId: String,
        @RequestBody clue: Clue
    ): ResponseEntity<GameData> {
        val gameData = codenamesService.giveClue(gameId, clue)
        return ResponseEntity.ok(gameData)
    }

    @PostMapping("/guess")
    fun makeGuess(
        @RequestParam gameId: String,
        @RequestParam guess: String,
    ): ResponseEntity<GameData> {
        val gameData = codenamesService.makeGuess(gameId, guess)
        return ResponseEntity.ok(gameData)
    }

    @DeleteMapping
    fun clearGames(): ResponseEntity.HeadersBuilder<*> {
        codenamesService.clear()
        return ResponseEntity.noContent()
    }
}
