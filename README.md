# Cool Language Compiler - Lexical and Syntactic Analysis

## What This Does

Transforms Cool source code into an Abstract Syntax Tree (AST) through two critical phases: breaking code into meaningful tokens (lexical analysis) and parsing those tokens according to Cool's grammar rules (syntactic analysis).

## How It Works

### Lexical Analysis Approach
- **Pattern Matching**: Uses ANTLR-generated finite automata to recognize Cool language constructs
- **Token Classification**: Categorizes source characters into tokens (keywords, identifiers, operators, literals)
- **Error Detection**: Identifies invalid character sequences, unterminated strings/comments, and malformed tokens
- **State Management**: Handles complex tokenization scenarios like nested comments and string escape sequences

### Syntactic Analysis Implementation
- **Grammar-Driven Parsing**: Implements Cool's complete context-free grammar using ANTLR's LL(*) parsing strategy
- **AST Construction**: Builds tree structures representing program hierarchy (classes → methods → expressions)
- **Precedence Handling**: Correctly parses expression precedence (arithmetic, comparison, dispatch operators)
- **Error Recovery**: Continues parsing after syntax errors to find multiple issues in one pass

## Key Technical Decisions

### Why ANTLR Over Hand-Written Parsers
- **Grammar Maintenance**: Language grammar changes require only grammar file updates, not code rewrites
- **Error Handling**: Built-in error recovery and reporting mechanisms
- **Performance**: Generated parsers are optimized for the specific grammar

### AST Design Philosophy
- **Strongly Typed Nodes**: Each syntax construct has a dedicated AST node class
- **Visitor Pattern Ready**: AST structure supports clean traversal for subsequent compiler phases
- **Position Tracking**: Maintains source code locations for error reporting in later phases

## Input/Output

**Input**: Cool source files (`.cl`) containing class definitions, methods, and expressions
**Output**: 
- Decorated AST with complete syntactic structure
- Comprehensive error reports with line/column positions
- Token stream representation (for debugging)

## Compiler Pipeline Integration

This is the **first phase** of the complete Cool compiler. The AST produced here serves as input to:

**Next Phase**: [Semantic Analysis](https://github.com/mehigh8/Cool_Compiler_SemanticAnalysis) - Validates type correctness and symbol resolution
**Final Phase**: [Code Generation](https://github.com/mehigh8/Cool_Compiler_CodeGeneration) - Produces executable MIPS assembly

The quality of this phase directly affects error message clarity and analysis efficiency in subsequent phases.
