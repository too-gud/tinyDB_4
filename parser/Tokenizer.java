package parser;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    public List<String> tokenize(String query) {

        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        int i = 0;

        while (i < query.length()) {

            char ch = query.charAt(i);

            // Whitespace ends the current token
            if (Character.isWhitespace(ch)) {
                flush(tokens, currentToken);
                i++;
                continue;
            }

            // Quoted string literal, e.g. 'Alice'. The quotes are kept as
            // part of the token so the parser can tell "1" (a number) apart
            // from "'1'" (a string that happens to look like a number).
            if (ch == '\'') {

                flush(tokens, currentToken);

                int closingQuote = query.indexOf('\'', i + 1);

                if (closingQuote == -1) {
                    throw new RuntimeException("Unterminated string literal: " + query.substring(i));
                }

                tokens.add(query.substring(i, closingQuote + 1));
                i = closingQuote + 1;
                continue;
            }

            // Two-character comparison operators: <=, >=, <>
            if (i + 1 < query.length()) {

                String twoChars = query.substring(i, i + 2);

                if (twoChars.equals("<=") || twoChars.equals(">=") || twoChars.equals("<>")) {
                    flush(tokens, currentToken);
                    tokens.add(twoChars);
                    i += 2;
                    continue;
                }
            }

            // Single-character SQL symbols
            if (ch == '(' ||
                ch == ')' ||
                ch == ',' ||
                ch == ';' ||
                ch == '=' ||
                ch == '<' ||
                ch == '>') {

                flush(tokens, currentToken);
                tokens.add(String.valueOf(ch));
                i++;
                continue;
            }

            // Part of a normal token (keyword, identifier, or number)
            currentToken.append(ch);
            i++;
        }

        // Add last token
        flush(tokens, currentToken);

        return tokens;
    }

    // Pushes whatever has been built up so far onto the token list, then
    // resets the buffer. Small helper to avoid repeating this everywhere.
    private void flush(List<String> tokens, StringBuilder currentToken) {
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
            currentToken.setLength(0);
        }
    }
}
