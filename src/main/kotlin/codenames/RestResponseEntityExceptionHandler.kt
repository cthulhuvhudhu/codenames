package codenames

import com.mongodb.MongoTimeoutException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice
class RestResponseEntityExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(value = [IllegalArgumentException::class, IllegalStateException::class, NullPointerException::class])
    protected fun handleClientError(
        ex: RuntimeException?, request: WebRequest?
    ): ResponseEntity<Any> {
        return handleExceptionInternal(
            ex!!, "Bad request",
            HttpHeaders(), HttpStatus.BAD_REQUEST, request!!
        )!!
    }

    @ExceptionHandler(value = [NoSuchElementException::class])
    protected fun handleDNE(
        ex: RuntimeException?, request: WebRequest?
    ): ResponseEntity<Any> {
        return handleExceptionInternal(
            ex!!, "Object not found!",
            HttpHeaders(), HttpStatus.NOT_FOUND, request!!
        )!!
    }

    @ExceptionHandler(value = [MongoTimeoutException::class])
    protected fun handleInternalError(
        ex: RuntimeException?, request: WebRequest?
    ): ResponseEntity<Any> {
        return handleExceptionInternal(
            ex!!, "Internal server error",
            HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request!!
        )!!
    }
}