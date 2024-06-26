package crux.ast;

import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NullNodeVisitor;
import crux.pt.CruxBaseVisitor;
import crux.pt.CruxParser;
import crux.pt.CruxParser.DeclContext;
import crux.pt.CruxParser.StmtContext;
import crux.pt.CruxParser.Op0Context;
import crux.pt.CruxParser.Op1Context;
import crux.pt.CruxParser.Op2Context;
import crux.pt.CruxParser.ParamContext;
import crux.pt.CruxParser.CallExprContext;
import crux.pt.CruxParser.Expr0Context;
import crux.ast.types.*;
import crux.ast.SymbolTable.Symbol;
import org.antlr.v4.runtime.ParserRuleContext;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class will convert the parse tree generated by ANTLR to AST It follows the visitor pattern
 * where decls will be by DeclVisitor Class Stmts will be resolved by StmtVisitor Class Exprs will
 * be resolved by ExprVisitor Class
 */

public final class ParseTreeLower {
  private final DeclVisitor declVisitor = new DeclVisitor();
  private final StmtVisitor stmtVisitor = new StmtVisitor();
  private final ExprVisitor exprVisitor = new ExprVisitor();

  private final SymbolTable symTab;

  public ParseTreeLower(PrintStream err) {
    symTab = new SymbolTable(err);
  }

  private static Position makePosition(ParserRuleContext ctx) {
    var start = ctx.start;
    return new Position(start.getLine());
  }
  private Type getType(String stype){
    if(stype.equals("int")) return new IntType();
    if(stype.equals("bool")) return new BoolType();
    if(stype.equals("void")) return new VoidType();
    return new ErrorType("No such type");
  }

  /**
   *
   * @return True if any errors
   */
  public boolean hasEncounteredError() {
    return symTab.hasEncounteredError();
  }


  /**
   * Lower top-level parse tree to AST
   *
   * @return a {@link DeclList} object representing the top-level AST.
   */

  public DeclarationList lower(CruxParser.ProgramContext program) {
    ArrayList<Declaration> list = new ArrayList<Declaration>();

    for(DeclContext context: program.declList().decl()) {
      Declaration node = context.accept(declVisitor);
      list.add(node);
    }

    return new DeclarationList(makePosition(program),list);
  }

  /**
   * Lower stmt list by lower individual stmt into AST.
   *
   * @return a {@link StmtList} AST object.
   */

  private StatementList lower(CruxParser.StmtListContext stmtList) {
    ArrayList<Statement> list = new ArrayList<>();

    for(StmtContext context: stmtList.stmt()) {
      Statement node = context.accept(stmtVisitor);
      list.add(node);
    }

    return new StatementList(makePosition(stmtList), list);
  }


  /**
   * Similar to {@link #lower(CruxParser.StmtListContext)}, but handles symbol table as well.
   *
   * @return a {@link StmtList} AST object.
   */
  private StatementList lower(CruxParser.StmtBlockContext stmtBlock) {
    symTab.enter();
    StatementList list = lower(stmtBlock.stmtList());
    symTab.exit();
    return list;
  }

  /**
   * A parse tree visitor to create AST nodes derived from {@link Declaration}
   */
  private final class DeclVisitor extends CruxBaseVisitor<Declaration> {
    /**
     * Visit a parse tree var decl and create an AST {@link VarariableDeclaration}
     *
     * @return an AST {@link VariableDeclaration}
     */
    //get the type of variable

     //@Override
     public VariableDeclaration visitVarDecl(CruxParser.VarDeclContext ctx) {
        String name = ctx.Identifier().getText();
        String stype = ctx.type().Identifier().getText();
        Type type = getType(stype);
        Symbol symbol = symTab.add(makePosition(ctx), name, type);
       return new VariableDeclaration(makePosition(ctx), symbol);
     }



    /**
     * Visit a parse tree array decl and creates an AST {@link ArrayDeclaration}
     *
     * @return an AST {@link ArrayDeclaration}
     */
    //@Override
     public Declaration visitArrayDecl(CruxParser.ArrayDeclContext ctx) {
        String name = ctx.Identifier().getText();
        String stype = ctx.type().Identifier().getText();
        Type baseType = getType(stype);
        long i = Long.parseLong(ctx.Integer().getText());
        Type type = new ArrayType(i, baseType);
        Symbol symbol = symTab.add(makePosition(ctx), name, type);
        return new ArrayDeclaration(makePosition(ctx), symbol);
     }



    /**
     * Visit a parse tree function definition and create an AST {@link FunctionDefinition}
     *
     * @return an AST {@link FunctionDefinition}
     */

    //@Override
     public Declaration visitFunctionDefn(CruxParser.FunctionDefnContext ctx) {
       //add function to symtable
       //function name
       String name = ctx.Identifier().getText();
       String stype = ctx.type().Identifier().getText();
       Type returnType = getType(stype);
       //add para to symtable
       List<Type> paraTypes = new ArrayList<>();
       List<Symbol> parameters =  new ArrayList<>();
       for(ParamContext para:ctx.paramList().param()) {
         String sparatype = para.type().Identifier().getText();
         Type paraType = getType(sparatype);
         paraTypes.add(paraType);
       }
       Type type = new FuncType(new TypeList(paraTypes), returnType);
       Symbol symbol = symTab.add(makePosition(ctx), name, type);
       symTab.enter();
       for(ParamContext para:ctx.paramList().param()){
         String sparatype = para.type().Identifier().getText();
         Type paraType = getType(sparatype);
         String paraName = para.Identifier().getText();
         Symbol paraSymbol= symTab.add(makePosition(ctx), paraName, paraType);
         parameters.add(paraSymbol);
       }
       StatementList statement = lower(ctx.stmtBlock());
       symTab.exit();

       return new FunctionDefinition(makePosition(ctx), symbol, parameters,statement);
     }

  }


  /**
   * A parse tree visitor to create AST nodes derived from {@link Stmt}
   */

  private final class StmtVisitor extends CruxBaseVisitor<Statement> {
    /**
     * Visit a parse tree var decl and create an AST {@link VariableDeclaration}. Since
     * {@link VariableDeclaration} is both {@link Declaration} and {@link Statement}, we simply
     * delegate this to {@link DeclVisitor#visitArrayDecl(CruxParser.ArrayDeclContext)} which we
     * implement earlier.
     *
     * @return an AST {@link VariableDeclaration}
     */


     //@Override
     public Statement visitVarDecl(CruxParser.VarDeclContext ctx) {
       String name = ctx.Identifier().getText();
       String stype = ctx.type().Identifier().getText();
       Type type = getType(stype);
       Symbol symbol = symTab.add(makePosition(ctx), name, type);
       return new VariableDeclaration(makePosition(ctx), symbol);
     }


    /**
     * Visit a parse tree assignment stmt and create an AST {@link Assignment}
     *
     * @return an AST {@link Assignment}
     */

    //@Override
    public Statement visitAssignStmt(CruxParser.AssignStmtContext ctx) {
      //ctx.designator().getText() is name
      String name = ctx.designator().Identifier().getText();
      Symbol symbol = symTab.lookup(makePosition(ctx),name);
      if(ctx.designator().expr0()!=null){
        //this is array assignment a[10]=10;
        Expression index = ctx.designator().expr0().accept(exprVisitor);
        ArrayAccess lhs = new ArrayAccess(makePosition(ctx), symbol, index);
        Expression rhs = ctx.expr0().accept(exprVisitor);
        return new Assignment(makePosition(ctx),lhs, rhs);
      }
      //this is variable assignment
      //a=10;
      VarAccess lhs = new VarAccess(makePosition(ctx), symbol);
      Expression rhs = ctx.expr0().accept(exprVisitor);
      return new Assignment(makePosition(ctx),lhs, rhs);
    }


    /**
     * Visit a parse tree call stmt and create an AST {@link Call}. Since {@link Call} is both
     * {@link Expression} and {@link Statementt}, we simply delegate this to
     * {@link ExprVisitor#visitCallExpr(CruxParser.CallExprContext)} that we will implement later.
     *
     * @return an AST {@link Call}
     */

    //@Override
     public Statement visitCallStmt(CruxParser.CallStmtContext ctx) {
        CallExprContext callee = ctx.callExpr();
        String name = callee.Identifier().getText();
        Symbol symbol = symTab.lookup(makePosition(ctx), name);
       ArrayList<Expression> arguments = new ArrayList<>();
       for(Expr0Context context: callee.exprList().expr0()) {
         Expression node = context.accept(exprVisitor);
         arguments.add(node);
       }
        return new Call(makePosition(ctx), symbol, arguments);
     }


    /**
     * Visit a parse tree if-else branch and create an AST {@link IfElseBranch}. The template code
     * shows partial implementations that visit the then block and else block recursively before
     * using those returned AST nodes to construct {@link IfElseBranch} object.
     *
     * @return an AST {@link IfElseBranch}
     */

     //@Override
     public Statement visitIfStmt(CruxParser.IfStmtContext ctx) {
        Expression condition = ctx.expr0().accept(exprVisitor);
        StatementList thenBlock = lower(ctx.stmtBlock(0));
        //else can be null
       StatementList elseBlock;
       if(ctx.stmtBlock(1)!=null){
         elseBlock = lower(ctx.stmtBlock(1));
       }else{
         elseBlock = new StatementList(makePosition(ctx), new ArrayList<>() {
         });
       }

        return new IfElseBranch(makePosition(ctx),condition,thenBlock, elseBlock);
     }


    /**
     * Visit a parse tree for loop and create an AST {@link Loop}. You'll going to use a similar
     * techniques as {@link #visitIfStmt(CruxParser.IfStmtContext)} to decompose this construction.
     *
     * @return an AST {@link Loop}
     */

     //@Override
     public Statement visitLoopStmt(CruxParser.LoopStmtContext ctx) {
        StatementList body = lower(ctx.stmtBlock());
        return new Loop(makePosition(ctx), body);
     }


    /**
     * Visit a parse tree return stmt and create an AST {@link Return}. Here we show a simple
     * example of how to lower a simple parse tree construction.
     *
     * @return an AST {@link Return}
     */
    //@Override
    public Statement visitReturnStmt(CruxParser.ReturnStmtContext ctx) {
      Expression value = ctx.expr0().accept(exprVisitor);
      return new Return(makePosition(ctx),value);
    }

    /**
     * Creates a Break node
     */
    //@Override
    public Statement visitBreakStmt(CruxParser.BreakStmtContext ctx) {
      return new Break(makePosition(ctx));
    }

    /**
     * Creates a Continue node
     */
    //@Override
    public Statement visitContinueStmt(CruxParser.ContinueStmtContext ctx) {
      return new Continue(makePosition(ctx));
    }
  }

  private final class ExprVisitor extends CruxBaseVisitor<Expression> {
    /**
     * Parse Expr0 to OpExpr Node Parsing the expr should be exactly as described in the grammer
     */
    //to get the operation0
    private Operation getOp0(Op0Context op){
      if(op.GreaterEqual() != null) return Operation.GE;
      if(op.LesserEqual() != null) return Operation.LE;
      if(op.NotEqual() != null) return Operation.NE;
      if(op.Equal() != null) return Operation.EQ;
      if(op.GreaterThan() != null) return Operation.GT;
      return Operation.LT;
    }
    // @Override
    public Expression visitExpr0(CruxParser.Expr0Context ctx) {
      //handle ">=" | "<=" | "!=" | "==" | ">" | "<"
      if (ctx.op0() == null) {
        //expr1 case
        return ctx.expr1(0).accept(exprVisitor);
      } else {
        //expr1 op0 expr1 case
        Expression lhs = ctx.expr1(0).accept(exprVisitor);
        Expression rhs = ctx.expr1(1).accept(exprVisitor);
        Op0Context op0 = ctx.op0();
        Operation op = getOp0(op0);
        return new OpExpr(makePosition(ctx), op, lhs, rhs);
      }

    }

    /**
     * Parse Expr1 to OpExpr Node Parsing the expr should be exactly as described in the grammer
     */
    private Operation getOp1(Op1Context op){
      if(op.Add() != null) return Operation.ADD;
      if(op.Sub() != null) return Operation.SUB;
      return Operation.LOGIC_OR;
    }
    //@Override
    public Expression visitExpr1(CruxParser.Expr1Context ctx) {
      //handle "+" | "-" | "||"
      if (ctx.op1() == null) {
        //expr2 case
        return ctx.expr2().accept(exprVisitor);
      } else {
        //expr1 op1 expr2 case
        Expression lhs = ctx.expr1().accept(exprVisitor);
        Expression rhs = ctx.expr2().accept(exprVisitor);
        Op1Context op1 = ctx.op1();
        Operation op = getOp1(op1);
        return new OpExpr(makePosition(ctx), op, lhs, rhs);
      }
    }


    /**
     * Parse Expr2 to OpExpr Node Parsing the expr should be exactly as described in the grammer
     */
    private Operation getOp2(Op2Context op){
      if(op.Mul() != null) return Operation.MULT;
      if(op.Div() != null) return Operation.DIV;
      return Operation.LOGIC_AND;
    }
    //@Override
    public Expression visitExpr2(CruxParser.Expr2Context ctx) {
      //handle //"*" | "/" | "&&"
      if (ctx.op2() == null) {
        //expr3 case
        return ctx.expr3().accept(exprVisitor);
      } else {
        //expr2 op2 expr3 case
        Expression lhs = ctx.expr2().accept(exprVisitor);
        Expression rhs = ctx.expr3().accept(exprVisitor);
        Op2Context op2 = ctx.op2();
        Operation op = getOp2(op2);
        return new OpExpr(makePosition(ctx), op, lhs, rhs);
      }
    }

    /**
     * Parse Expr3 to OpExpr Node Parsing the expr should be exactly as described in the grammer
     */
    //@Override
    public Expression visitExpr3(CruxParser.Expr3Context ctx) {
        if(ctx.literal()!=null){
          return ctx.literal().accept(exprVisitor);
        }else if(ctx.designator()!=null){
          return ctx.designator().accept(exprVisitor);
        }else if(ctx.callExpr()!=null) {
          return ctx.callExpr().accept(exprVisitor);
        }else if(ctx.expr0()!=null){
          return ctx.expr0().accept(exprVisitor);
        }else{
          Expression lhs =ctx.expr3().accept(exprVisitor);
          Operation op = Operation.LOGIC_NOT;
          return new OpExpr(makePosition(ctx),op,lhs , null);
        }
    }

    /**
     * Create an Call Node
     */
    //@Override
    public Call visitCallExpr(CruxParser.CallExprContext ctx) {
      String name = ctx.Identifier().getText();
      Symbol symbol = symTab.lookup(makePosition(ctx), name);
      ArrayList<Expression> arguments = new ArrayList<>();
      for(Expr0Context context: ctx.exprList().expr0()) {
        Expression node = context.accept(exprVisitor);
        arguments.add(node);
      }
      return new Call(makePosition(ctx), symbol, arguments);
    }

    /**
     * visitDesignator will check for a name or ArrayAccess FYI it should account for the case when
     * the designator was dereferenced
     */
    //@Override
    public Expression visitDesignator(CruxParser.DesignatorContext ctx) {
      String name = ctx.Identifier().getText();
      Symbol symbol = symTab.lookup(makePosition(ctx),name);
      //then access array through index
      if(ctx.expr0()!=null){
        Expression index = ctx.expr0().accept(exprVisitor);
        return new ArrayAccess(makePosition(ctx), symbol, index);
      }else{
        return new VarAccess(makePosition(ctx), symbol);
      }

    }

    /**
     * Create an Literal Node
     */
    //@Override
    public Expression visitLiteral(CruxParser.LiteralContext ctx) {
      if(ctx.Integer()!=null){
        long i = Long.parseLong(ctx.Integer().getText());
        return new LiteralInt(makePosition(ctx), i);
      }else if(ctx.True()!=null){
        return new LiteralBool(makePosition(ctx), true);
      }else{
        return new LiteralBool(makePosition(ctx), false);
      }
    }
  }
}
