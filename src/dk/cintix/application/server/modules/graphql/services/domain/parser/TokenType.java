package dk.cintix.application.server.modules.graphql.services.domain.parser;

public enum TokenType {
    NAME,
    NUMBER,
    STRING,
    TRUE,
    FALSE,
    NULL,
    LBRACE,   // {
    RBRACE,   // }
    LPAREN,   // (
    RPAREN,   // )
    COLON,    // :
    COMMA,    // ,
    EOF
}
