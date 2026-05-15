package dk.cintix.application.server.modules.http.server.services.graphql.parser;

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
