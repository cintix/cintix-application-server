package dk.cintix.application.server.modules.graphql.services.domain.parser;

import java.util.*;
import dk.cintix.application.server.modules.graphql.services.domain.GraphQLException;
import dk.cintix.application.server.modules.graphql.services.domain.ast.*;

public class Parser {
    public static final int DEFAULT_MAX_DEPTH = 10;
    public static final int DEFAULT_MAX_SELECTIONS = 100;

    private final List<Token> tokens;
    private final int maxDepth;
    private final int maxSelections;
    private int pos = 0;
    private int selectionCount = 0;

    public Parser(String text) {
        this(text, DEFAULT_MAX_DEPTH, DEFAULT_MAX_SELECTIONS);
    }

    public Parser(String text, int maxDepth, int maxSelections) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxSelections < 1) {
            throw new IllegalArgumentException("maxSelections must be positive");
        }
        this.maxDepth = maxDepth;
        this.maxSelections = maxSelections;
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
            throw new GraphQLException(message + ". Found: " + current());
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
        op.getSelections().addAll(parseSelections(1));
        return op;
    }

    private List<Selection> parseSelections(int depth) {
        if (depth > maxDepth) {
            throw new GraphQLException("Query exceeds maximum depth of " + maxDepth);
        }

        List<Selection> selections = new ArrayList<>();
        while (!match(TokenType.RBRACE) && current().getType() != TokenType.EOF) {
            Token nameTok = expect(TokenType.NAME, "Expected field name");
            Selection sel = new Selection(nameTok.getText());
            registerSelection();

            if (match(TokenType.LPAREN)) {
                sel.getArguments().putAll(parseArguments());
            }

            if (match(TokenType.LBRACE)) {
                sel.getSubSelections().addAll(parseSelections(depth + 1));
            }

            selections.add(sel);
            match(TokenType.COMMA);
        }
        return selections;
    }

    private void registerSelection() {
        selectionCount++;
        if (selectionCount > maxSelections) {
            throw new GraphQLException("Query exceeds maximum of " + maxSelections + " selections");
        }
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
            default: throw new GraphQLException("Unexpected value token " + current());
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
