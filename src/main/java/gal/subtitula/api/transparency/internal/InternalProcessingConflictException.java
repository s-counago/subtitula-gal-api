package gal.subtitula.api.transparency.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InternalProcessingConflictException extends RuntimeException {
    public InternalProcessingConflictException(String message) {
        super(message);
    }
}
