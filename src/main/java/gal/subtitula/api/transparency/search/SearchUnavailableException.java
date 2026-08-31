package gal.subtitula.api.transparency.search;

public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException() {
        super("Public search is not available");
    }
}
