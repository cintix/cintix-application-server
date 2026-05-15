package dk.cintix.application.server.modules.http.server.services.graphql.parser;

import java.util.*;

public class Lexer {
    private final String text;
    private int pos = 0;

    public Lexer(String text) { this.text = text; }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c)) { pos++; continue; }

            switch (c) {
                case '{': tokens.add(new Token(TokenType.LBRACE, "{")); pos++; break;
                case '}': tokens.add(new Token(TokenType.RBRACE, "}")); pos++; break;
                case '(': tokens.add(new Token(TokenType.LPAREN, "(")); pos++; break;
                case ')': tokens.add(new Token(TokenType.RPAREN, ")")); pos++; break;
                case ':': tokens.add(new Token(TokenType.COLON, ":")); pos++; break;
                case ',': tokens.add(new Token(TokenType.COMMA, ",")); pos++; break;
                case '"': tokens.add(new Token(TokenType.STRING, readString())); break;
                default:
                    if (Character.isDigit(c)) {
                        tokens.add(new Token(TokenType.NUMBER, readNumber()));
                    } else if (Character.isLetter(c) || c == '_') {
                        String name = readName();
                        switch (name) {
                            case "true": tokens.add(new Token(TokenType.TRUE, name)); break;
                            case "false": tokens.add(new Token(TokenType.FALSE, name)); break;
                            case "null": tokens.add(new Token(TokenType.NULL, name)); break;
                            default: tokens.add(new Token(TokenType.NAME, name)); break;
                        }
                    } else {
                        throw new RuntimeException("Unexpected char: " + c + " at " + pos);
                    }
            }
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private String readString() {
        pos++; // skip "
        StringBuilder sb = new StringBuilder();
        while (pos < text.length() && text.charAt(pos) != '"') {
            sb.append(text.charAt(pos++));
        }
        if (pos >= text.length()) throw new RuntimeException("Unterminated string");
        pos++; // skip closing "
        return sb.toString();
    }

    private String readNumber() {
        StringBuilder sb = new StringBuilder();
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
            sb.append(text.charAt(pos++));
        }
        return sb.toString();
    }

    private String readName() {
        StringBuilder sb = new StringBuilder();
        while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
            sb.append(text.charAt(pos++));
        }
        return sb.toString();
    }
}
