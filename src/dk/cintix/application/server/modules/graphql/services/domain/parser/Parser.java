package dk.cintix.application.server.modules.graphql.services.domain.parser;

import java.util.*;
import dk.cintix.application.server.modules.graphql.services.domain.ast.*;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(String text) {
        Lexer lexer = new Lexer(text);
        this.tokens = lexer.tokenize();
    }

    private Token current() { return tokens.get(pos); }
    private void advance() { if (pos < tokens.size()) pos++; }
    private boolean match(TokenType type) {
        if (current().getType() == type) { advance(); return true; }
        return false;
    }

    private Token expect(TokenType type, String message) {
        if (current().getType() != type)
            throw new RuntimeException(message + ". Found: " + current());
        Token tok = current();
        advance();
        return tok;
    }

    public Document parse() {
        Document doc = new Document();
        doc.setOperation(parseOperation());
        expect(TokenType.EOF, "Expected end of document");
        return doc;
    }

    private Operation parseOperation() {
        Operation op = new Operation();
        if (current().getType() == TokenType.NAME) {
            String name = current().getText();
            if ("query".equals(name)) { op.setType(OperationType.QUERY); advance(); }
            else if ("mutation".equals(name)) { op.setType(OperationType.MUTATION); advance(); }
        }
        expect(TokenType.LBRACE, "Expected { after operation");
        op.getSelections().addAll(parseSelections());
        return op;
    }

    private List<Selection> parseSelections() {
        List<Selection> selections = new ArrayList<>();
        while (!match(TokenType.RBRACE) && current().getType() != TokenType.EOF) {
            Token nameTok = expect(TokenType.NAME, "Expected field name");
            Selection sel = new Selection(nameTok.getText());

            if (match(TokenType.LPAREN)) {
                sel.getArguments().putAll(parseArguments());
                // Removed problematic expect(TokenType.RPAREN)
            }

            if (match(TokenType.LBRACE)) {
                sel.getSubSelections().addAll(parseSelections());
            }

            selections.add(sel);
            match(TokenType.COMMA);
        }
        return selections;
    }

    private Map<String, Value> parseArguments() {
        Map<String, Value> args = new HashMap<>();
        while (current().getType() != TokenType.RPAREN && current().getType() != TokenType.EOF) {
            Token keyTok = expect(TokenType.NAME, "Expected argument name");
            expect(TokenType.COLON, "Expected : after argument name");
            Value val = parseValue();
            args.put(keyTok.getText(), val);
            match(TokenType.COMMA);
        }
        // Consume RPAREN if present
        if (current().getType() == TokenType.RPAREN) advance();
        return args;
    }

    private Value parseValue() {
        switch (current().getType()) {
            case STRING: String s = current().getText(); advance(); return new StringValue(s);
            case NUMBER: String n = current().getText(); advance(); return new NumberValue(n);
            case TRUE: advance(); return new BooleanValue(true);
            case FALSE: advance(); return new BooleanValue(false);
            case NULL: advance(); return new NullValue();
            case LBRACE: return parseObjectValue();
            case NAME: String name = current().getText(); advance(); return new EnumValue(name);
            default: throw new RuntimeException("Unexpected value token " + current());
        }
    }

    private ObjectValue parseObjectValue() {
        expect(TokenType.LBRACE, "Expected { at start of object");
        ObjectValue obj = new ObjectValue();
        while (!match(TokenType.RBRACE) && current().getType() != TokenType.EOF) {
            Token keyTok = expect(TokenType.NAME, "Expected field name in object");
            expect(TokenType.COLON, "Expected : after field name");
            Value val = parseValue();
            obj.getFields().put(keyTok.getText(), val);
            match(TokenType.COMMA);
        }
        return obj;
    }
}
