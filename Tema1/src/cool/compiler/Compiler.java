package cool.compiler;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import cool.lexer.*;
import cool.parser.*;

import java.io.*;
import java.util.stream.Collectors;


public class Compiler {
    // Annotates class nodes with the names of files where they are defined.
    public static ParseTreeProperty<String> fileNames = new ParseTreeProperty<>();

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("No file(s) given");
            return;
        }
        
        CoolLexer lexer = null;
        CommonTokenStream tokenStream = null;
        CoolParser parser = null;
        ParserRuleContext globalTree = null;
        
        // True if any lexical or syntax errors occur.
        boolean lexicalSyntaxErrors = false;
        
        // Parse each input file and build one big parse tree out of
        // individual parse trees.
        for (var fileName : args) {
            var input = CharStreams.fromFileName(fileName);
            
            // Lexer
            if (lexer == null)
                lexer = new CoolLexer(input);
            else
                lexer.setInputStream(input);

            // Token stream
            if (tokenStream == null)
                tokenStream = new CommonTokenStream(lexer);
            else
                tokenStream.setTokenSource(lexer);
                

//            // Test lexer only.
//            tokenStream.fill();
//            List<Token> tokens = tokenStream.getTokens();
//            tokens.stream().forEach(token -> {
//                var text = token.getText();
//                var name = CoolLexer.VOCABULARY.getSymbolicName(token.getType());
//
//                System.out.println(text + " : " + name);
//                //System.out.println(token);
//            });

            
            // Parser
            if (parser == null)
                parser = new CoolParser(tokenStream);
            else
                parser.setTokenStream(tokenStream);
            
            // Customized error listener, for including file names in error
            // messages.
            var errorListener = new BaseErrorListener() {
                public boolean errors = false;
                
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer,
                                        Object offendingSymbol,
                                        int line, int charPositionInLine,
                                        String msg,
                                        RecognitionException e) {
                    String newMsg = "\"" + new File(fileName).getName() + "\", line " +
                                        line + ":" + (charPositionInLine + 1) + ", ";
                    
                    Token token = (Token)offendingSymbol;
                    if (token.getType() == CoolLexer.ERROR)
                        newMsg += "Lexical error: " + token.getText();
                    else
                        newMsg += "Syntax error: " + msg;
                    
                    System.err.println(newMsg);
                    errors = true;
                }
            };
            
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            
            // Actual parsing
            var tree = parser.program();
            if (globalTree == null)
                globalTree = tree;
            else
                // Add the current parse tree's children to the global tree.
                for (int i = 0; i < tree.getChildCount(); i++)
                    globalTree.addAnyChild(tree.getChild(i));
                    
            // Annotate class nodes with file names, to be used later
            // in semantic error messages.
            for (int i = 0; i < tree.getChildCount(); i++) {
                var child = tree.getChild(i);
                // The only ParserRuleContext children of the program node
                // are class nodes.
                if (child instanceof ParserRuleContext)
                    fileNames.put(child, fileName);
            }
            
            // Record any lexical or syntax errors.
            lexicalSyntaxErrors |= errorListener.errors;
        }

        // Stop before semantic analysis phase, in case errors occurred.
        if (lexicalSyntaxErrors) {
            System.err.println("Compilation halted");
            return;
        }

        var astConstructionVisitor = new CoolParserBaseVisitor<ASTNode>() {
            @Override
            public ASTNode visitProgram(CoolParser.ProgramContext ctx) {
                return new Program(ctx,
                                   ctx.start,
                                   ctx.classes.stream().map(cls -> (Class)visit(cls)).collect(Collectors.toList()));
            }

            @Override
            public ASTNode visitClass(CoolParser.ClassContext ctx) {
                return new Class(ctx,
                                 ctx.start,
                                 ctx.classId,
                                 ctx.parentClassId,
                                 ctx.features.stream().map(ftr -> (Feature)visit(ftr)).collect(Collectors.toList()));
            }

            @Override
            public ASTNode visitFuncFeature(CoolParser.FuncFeatureContext ctx) {
                return new FuncFeature(ctx,
                                       ctx.start,
                                       ctx.formals.stream().map(frml -> (Formal)visit(frml)).collect(Collectors.toList()),
                                       ctx.funcId,
                                       ctx.funcType,
                                       (Expression) visit(ctx.e));
            }

            @Override
            public ASTNode visitVarFeature(CoolParser.VarFeatureContext ctx) {
                return new VarFeature(ctx,
                                      ctx.start,
                                      ctx.varId,
                                      ctx.varType,
                                      (ctx.e == null) ? null : (Expression) visit(ctx.e));
            }

            @Override
            public ASTNode visitFormal(CoolParser.FormalContext ctx) {
                return new Formal(ctx,
                                  ctx.start,
                                  ctx.formalId,
                                  ctx.formalType);
            }

            @Override
            public ASTNode visitExplicitDispatch(CoolParser.ExplicitDispatchContext ctx) {
                return new ExplicitDispatch(ctx,
                                            ctx.start,
                                            (Expression) visit(ctx.obj),
                                            ctx.parentType,
                                            new ImplicitDispatch(ctx,
                                                                 ctx.start,
                                                                 ctx.funcId,
                                                                 ctx.funcParams.stream().map(fp -> (Expression)visit(fp)).collect(Collectors.toList()),
                                                                 true));
            }

            @Override
            public ASTNode visitImplicitDispatch(CoolParser.ImplicitDispatchContext ctx) {
                return new ImplicitDispatch(ctx,
                                            ctx.start,
                                            ctx.funcId,
                                            ctx.funcParams.stream().map(fp -> (Expression)visit(fp)).collect(Collectors.toList()),
                                            false);
            }

            @Override
            public ASTNode visitIf(CoolParser.IfContext ctx) {
                return new If(ctx,
                              ctx.start,
                              (Expression)visit(ctx.cond),
                              (Expression)visit(ctx.thenBranch),
                              (Expression)visit(ctx.elseBranch));
            }

            @Override
            public ASTNode visitWhile(CoolParser.WhileContext ctx) {
                return new While(ctx,
                                 ctx.start,
                                 (Expression)visit(ctx.cond),
                                 (Expression)visit(ctx.content));
            }

            @Override
            public ASTNode visitBlock(CoolParser.BlockContext ctx) {
                return new Block(ctx,
                                 ctx.start,
                                 ctx.exprs.stream().map(expr -> (Expression)visit(expr)).collect(Collectors.toList()));
            }

            @Override
            public ASTNode visitLocal(CoolParser.LocalContext ctx) {
                return new Local(ctx,
                                 ctx.start,
                                 ctx.varId,
                                 ctx.varType,
                                 (ctx.varExpr == null) ? null : (Expression)visit(ctx.varExpr));
            }

            @Override
            public ASTNode visitLet(CoolParser.LetContext ctx) {
                return new Let(ctx,
                               ctx.start,
                               ctx.localList.stream().map(local -> (Local)visit(local)).collect(Collectors.toList()),
                               (Expression)visit(ctx.letContent));
            }

            @Override
            public ASTNode visitCaseBranch(CoolParser.CaseBranchContext ctx) {
                return new CaseBranch(ctx,
                                      ctx.start,
                                      ctx.varId,
                                      ctx.varType,
                                      (Expression)visit(ctx.branchExpr));
            }

            @Override
            public ASTNode visitCase(CoolParser.CaseContext ctx) {
                return new Case(ctx,
                                ctx.start,
                                (Expression)visit(ctx.caseExpr),
                                ctx.caseBranches.stream().map(cbr -> (CaseBranch)visit(cbr)).collect(Collectors.toList()));
            }

            @Override
            public ASTNode visitNew(CoolParser.NewContext ctx) {
                return new New(ctx,
                               ctx.start,
                               ctx.initType);
            }

            @Override
            public ASTNode visitCompl(CoolParser.ComplContext ctx) {
                return new Compl(ctx,
                                 ctx.start,
                                 (Expression)visit(ctx.e));
            }

            @Override
            public ASTNode visitIsvoid(CoolParser.IsvoidContext ctx) {
                return new Isvoid(ctx,
                                  ctx.start,
                                  (Expression)visit(ctx.e));
            }

            @Override
            public ASTNode visitMultDiv(CoolParser.MultDivContext ctx) {
                return new MultDiv(ctx,
                                   ctx.start,
                                   (Expression)visit(ctx.left),
                                   (Expression)visit(ctx.right),
                                   ctx.op);
            }

            @Override
            public ASTNode visitPlusMinus(CoolParser.PlusMinusContext ctx) {
                return new PlusMinus(ctx,
                                     ctx.start,
                                     (Expression)visit(ctx.left),
                                     (Expression)visit(ctx.right),
                                     ctx.op);
            }

            @Override
            public ASTNode visitRelational(CoolParser.RelationalContext ctx) {
                return new Relational(ctx,
                                      ctx.start,
                                      (Expression)visit(ctx.left),
                                      (Expression)visit(ctx.right),
                                      ctx.op);
            }

            @Override
            public ASTNode visitNot(CoolParser.NotContext ctx) {
                return new Not(ctx,
                               ctx.start,
                               (Expression)visit(ctx.e));
            }

            @Override
            public ASTNode visitAssign(CoolParser.AssignContext ctx) {
                return new Assign(ctx,
                                  ctx.start,
                                  ctx.varId,
                                  (Expression)visit(ctx.e));
            }

            @Override
            public ASTNode visitParen(CoolParser.ParenContext ctx) {
                return new Paren(ctx,
                                 ctx.start,
                                 (Expression)visit(ctx.e));
            }

            @Override
            public ASTNode visitId(CoolParser.IdContext ctx) {
                return new Id(ctx,
                              ctx.start,
                              ctx.varId);
            }

            @Override
            public ASTNode visitInteger(CoolParser.IntegerContext ctx) {
                return new IntegerExpr(ctx,
                                       ctx.start);
            }

            @Override
            public ASTNode visitString(CoolParser.StringContext ctx) {
                return new StringExpr(ctx,
                                      ctx.start);
            }

            @Override
            public ASTNode visitBool(CoolParser.BoolContext ctx) {
                return new BoolExpr(ctx,
                                    ctx.start);
            }
        };

        var ast = astConstructionVisitor.visit(globalTree);

        var printVisitor = new ASTVisitor<Void>() {
            int indent = 0;

            void printIndent(String str) {
                for (int i = 0; i < indent; i++)
                    System.out.print("  ");
                System.out.println(str);
            }

            @Override
            public Void visit(Program program) {
                printIndent("program");
                indent++;
                for (Class classs : program.classes)
                    classs.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Class classs) {
                printIndent("class");
                indent++;
                printIndent(classs.classId.getText());
                if (classs.parentClassId != null)
                    printIndent(classs.parentClassId.getText());
                for (Feature feature : classs.features)
                    feature.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(FuncFeature funcFeature) {
                printIndent("method");
                indent++;
                printIndent(funcFeature.funcId.getText());
                for (Formal formal : funcFeature.formals)
                    formal.accept(this);
                printIndent(funcFeature.funcType.getText());
                funcFeature.e.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(VarFeature varFeature) {
                printIndent("attribute");
                indent++;
                printIndent(varFeature.varId.getText());
                printIndent(varFeature.varType.getText());
                if (varFeature.e != null)
                    varFeature.e.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Formal formal) {
                printIndent("formal");
                indent++;
                printIndent(formal.formalId.getText());
                printIndent(formal.formalType.getText());
                indent--;
                return null;
            }

            @Override
            public Void visit(ExplicitDispatch explicitDispatch) {
                printIndent(".");
                indent++;
                explicitDispatch.obj.accept(this);
                if (explicitDispatch.parentType != null)
                    printIndent(explicitDispatch.parentType.getText());
                explicitDispatch.dispatch.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(ImplicitDispatch implicitDispatch) {
                if (!implicitDispatch.fromExplicit) {
                    printIndent("implicit dispatch");
                    indent++;
                }
                printIndent(implicitDispatch.funcId.getText());
                for (Expression expr : implicitDispatch.funcParams)
                    expr.accept(this);
                if (!implicitDispatch.fromExplicit)
                    indent--;
                return null;
            }

            @Override
            public Void visit(If iff) {
                printIndent("if");
                indent++;
                iff.cond.accept(this);
                iff.thenBranch.accept(this);
                iff.elseBranch.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(While whilee) {
                printIndent("while");
                indent++;
                whilee.cond.accept(this);
                whilee.content.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Block block) {
                printIndent("block");
                indent++;
                for (Expression expr : block.exprs)
                    expr.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Local local) {
                printIndent("local");
                indent++;
                printIndent(local.varId.getText());
                printIndent(local.varType.getText());
                if (local.varExpr != null)
                    local.varExpr.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Let let) {
                printIndent("let");
                indent++;
                for (Local local : let.localList)
                    local.accept(this);
                let.letContent.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(CaseBranch caseBranch) {
                printIndent("case branch");
                indent++;
                printIndent(caseBranch.varId.getText());
                printIndent(caseBranch.varType.getText());
                caseBranch.branchExpr.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Case casee) {
                printIndent("case");
                indent++;
                casee.caseExpr.accept(this);
                for (CaseBranch caseBranch : casee.caseBranches)
                    caseBranch.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(New neww) {
                printIndent("new");
                indent++;
                printIndent(neww.initType.getText());
                indent--;
                return null;
            }

            @Override
            public Void visit(Compl compl) {
                printIndent("~");
                indent++;
                compl.e.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Isvoid isvoid) {
                printIndent("isvoid");
                indent++;
                isvoid.e.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(MultDiv multDiv) {
                printIndent(multDiv.op.getText());
                indent++;
                multDiv.left.accept(this);
                multDiv.right.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(PlusMinus plusMinus) {
                printIndent(plusMinus.op.getText());
                indent++;
                plusMinus.left.accept(this);
                plusMinus.right.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Relational relational) {
                printIndent(relational.op.getText());
                indent++;
                relational.left.accept(this);
                relational.right.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Not not) {
                printIndent("not");
                indent++;
                not.e.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Assign assign) {
                printIndent("<-");
                indent++;
                printIndent(assign.varId.getText());
                assign.e.accept(this);
                indent--;
                return null;
            }

            @Override
            public Void visit(Paren paren) {
                paren.e.accept(this);
                return null;
            }

            @Override
            public Void visit(Id id) {
                printIndent(id.varId.getText());
                return null;
            }

            @Override
            public Void visit(IntegerExpr integerExpr) {
                printIndent(integerExpr.start.getText());
                return null;
            }

            @Override
            public Void visit(StringExpr stringExpr) {
                printIndent(stringExpr.start.getText());
                return null;
            }

            @Override
            public Void visit(BoolExpr boolExpr) {
                printIndent(boolExpr.start.getText());
                return null;
            }
        };

        ast.accept(printVisitor);
    }
}
