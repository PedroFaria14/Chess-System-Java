package dev.chess.infrastructure.web;

import chess.ChessException;
import dev.chess.application.EngineException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ChessException.class)
    ProblemDetail illegalMove(ChessException e) { return problem(422, "Jogada não permitida", e.getMessage()); }
    @ExceptionHandler(EngineException.class)
    ProblemDetail engine(EngineException e) { return problem(503, "Computador indisponível", e.getMessage()); }
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException e) { return problem(e.getStatusCode().value(), "Solicitação não concluída", e.getReason()); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflict(OptimisticLockingFailureException e) { return problem(409, "Partida atualizada", "Outra jogada foi salva. Atualize a partida antes de continuar."); }
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException e) { return problem(400, "Dados inválidos", "Verifique os parâmetros informados."); }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(400, "Dados inválidos", "Informe nomes de até 40 caracteres, casas de a1 a h8 e uma versão válida."));
    }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erro inesperado na API", e);
        return problem(500, "Erro interno", "Não foi possível concluir a operação. Tente novamente.");
    }
    private ProblemDetail problem(int status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(title); return problem;
    }
}
