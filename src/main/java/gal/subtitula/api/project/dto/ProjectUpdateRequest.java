package gal.subtitula.api.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gal.subtitula.api.project.Word;
import java.util.List;

/**
 * Partial update — any null field is left unchanged. {@code approved} is
 * one-way: true approves the session, and nothing un-approves it.
 */
public record ProjectUpdateRequest(String name, List<Word> words, JsonNode style, Double speedFactor,
                                   JsonNode baseBox, JsonNode segments, Boolean approved) {}
