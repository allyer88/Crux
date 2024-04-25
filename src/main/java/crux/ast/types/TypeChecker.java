package crux.ast.types;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.traversal.NullNodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class will associate types with the AST nodes from Stage 2
 */


public final class TypeChecker {
  private final ArrayList<String> errors = new ArrayList<>();

  public ArrayList<String> getErrors() {
    return errors;
  }

  //Stores the symbol of the function node currently being traversed.
  //Used to specify functions in error messages.
  public Symbol currentFunctionSymbol;

  //Stores the return type of the Function currently being traversed.
  //Used to verify returned  values have the correct types
  public Type currentFunctionReturnType;

  public void check(DeclarationList ast) {
    var inferenceVisitor = new TypeInferenceVisitor();
    inferenceVisitor.visit(ast);
  }

  /**
   * Helper function, should be used to add error into the errors array
   */
  private void addTypeError(Node n, String message) {
    errors.add(String.format("TypeError%s[%s]", n.getPosition(), message));
  }

  /**
   * Helper function, should be used to record Types if the Type is an ErrorType then it will call
   * addTypeError
   */
  private void setNodeType(Node n, Type ty) {
    ((BaseNode) n).setType(ty);
    if (ty.getClass() == ErrorType.class) {
      var error = (ErrorType) ty;
      addTypeError(n, error.getMessage());
    }
  }

  /**
   * Helper to retrieve Type from the map
   */
  public Type getType(Node n) {
    return ((BaseNode) n).getType();
  }


  /**
   * This calls will visit each AST node and try to resolve it's type with the help of the
   * symbolTable.
   */
  private final class TypeInferenceVisitor extends NullNodeVisitor<Void> {
    @Override
    public Void visit(VarAccess vaccess) {
      Type type = vaccess.getSymbol().getType();
      setNodeType(vaccess, type);
      return null;
    }

    @Override
    public Void visit(ArrayDeclaration arrayDeclaration) {
      Type type = arrayDeclaration.getSymbol().getType();
      //CAST TO Array type
      ArrayType aType = (ArrayType)type;
      if(aType.getBase().getClass()!= IntType.class && aType.getBase().getClass()!= BoolType.class){
        setNodeType(arrayDeclaration, new ErrorType("The (base) type is either IntType or BoolType"));
      }
      return null;
    }

    @Override
    public Void visit(Assignment assignment) {
      Expression location  = assignment.getLocation();
      Expression value = assignment.getValue();
      location.accept(this);
      value.accept(this);
      Type locType = getType(location);
      Type valType = getType(value);
      Type type = locType.assign(valType);
      setNodeType(assignment, type);
      return null;
    }

    @Override
    public Void visit(Break brk) {
      return null;
    }

    @Override
    public Void visit(Call call) {
      List<Expression> arguments = call.getArguments();
      TypeList typeList = new TypeList();
      //get FuncType
      Type callee = call.getType();
      //visit arguments and call
      for(Expression exp: arguments){
        exp.accept(this);
        Type expType = getType(exp);
        Type type = callee.call(expType);
        typeList.append(type);
      }
      setNodeType(call, typeList);
      return null;
    }

    @Override
    public Void visit(Continue cont) {
      return null;
    }
    
    @Override
    public Void visit(DeclarationList declarationList) {
      declarationList.accept(this);
      return null;
    }

    @Override
    public Void visit(FunctionDefinition functionDefinition) {
      currentFunctionSymbol = functionDefinition.getSymbol();
      FuncType funcType = (FuncType)currentFunctionSymbol.getType();
      currentFunctionReturnType = funcType.getRet();
      //If function is main, verify that return type is void and there are no parameters.
      if(currentFunctionSymbol.getName().equals("main")){
        if(!currentFunctionReturnType.equivalent(new VoidType())){
          setNodeType(functionDefinition, new ErrorType("Return type should be void"));
        }
        if(!functionDefinition.getParameters().isEmpty()){
          setNodeType(functionDefinition, new ErrorType("main() should not include parameters"));
        }
      }
      //Verify all parameters are IntType or BoolType.
      for(Type type: funcType.getArgs()){
        funcType.call(type);
      }
      StatementList statements = functionDefinition.getStatements();
      statements.accept(this);
      return null;
    }

    @Override
    public Void visit(IfElseBranch ifElseBranch) {
      Expression condition = ifElseBranch.getCondition();
      condition.accept(this);
      Type condType = getType(condition);
      //Get type of condition and verify that it is BoolType.
      if(condType.getClass() != BoolType.class){
        setNodeType(ifElseBranch, new ErrorType("condition should be BoolType."));
      }
      //Visit thenBlock.
      StatementList thenBlock = ifElseBranch.getThenBlock();
      thenBlock.accept(this);
      //Visit elseBlock if not null.
      if(ifElseBranch.getElseBlock()!=null){
        StatementList elseBlock = ifElseBranch.getElseBlock();
        elseBlock.accept(this);
      }
      return null;
    }

    @Override
    public Void visit(ArrayAccess access) {
      ArrayType arrayType = (ArrayType) access.getType();
      Expression index = access.getIndex();
      Type indexType = getType(index);
      Type type = arrayType.index(indexType);
      //Set the node type to the result of Type.index().
      setNodeType(access, type);
      return null;
    }

    @Override
    public Void visit(LiteralBool literalBool) {
      setNodeType(literalBool, new BoolType());
      return null;
    }

    @Override
    public Void visit(LiteralInt literalInt) {
      setNodeType(literalInt, new IntType());
      return null;
    }

    @Override
    public Void visit(Loop loop) {
      StatementList body = loop.getBody();
      body.accept(this);
      return null;
    }

    @Override
    public Void visit(OpExpr op) {
      Expression left = op.getLeft();
      Expression right = op.getRight();
      //Get the Types of the expressions.
      left.accept(this);
      right.accept(this);
      Type leftType = getType(left);
      Type rightType = getType(right);
      Type resulType;
      OpExpr.Operation operation=op.getOp();
      if(operation== OpExpr.Operation.ADD){
        resulType = leftType.add(rightType);
      }else if(operation == OpExpr.Operation.SUB){
        resulType = leftType.sub(rightType);
      }else if(operation == OpExpr.Operation.MULT){
        resulType = leftType.mul(rightType);
      }else if(operation == OpExpr.Operation.DIV){
        resulType = leftType.div(rightType);
      }else if(operation == OpExpr.Operation.LOGIC_AND){
        resulType = leftType.and(rightType);
      }else if(operation == OpExpr.Operation.LOGIC_OR){
        resulType = leftType.or(rightType);
      }else if(operation == OpExpr.Operation.LOGIC_NOT){
        resulType = leftType.not();
      }else { //compare type
        resulType = leftType.compare(rightType);
      }
      setNodeType(op, resulType);
      return null;
    }

    @Override
    public Void visit(Return ret) {
      Expression value = ret.getValue();
      //Visit Expression.
      value.accept(this);
      Type returnType = getType(value);
      //Verify expression type is equivalent to currentFunctionReturnType.
      if(returnType.getClass()!=currentFunctionReturnType.getClass()){
        setNodeType(ret, new ErrorType("Return type is incorrect"));
      }
      return null;
    }

    @Override
    public Void visit(StatementList statementList) {
      statementList.accept(this);
      return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDeclaration) {
      Type type = variableDeclaration.getSymbol().getType();
      //CAST TO Array type
      if(type.getClass()!= IntType.class && type.getClass()!= BoolType.class){
        setNodeType(variableDeclaration, new ErrorType("The type is either IntType or BoolType"));
      }
      return null;
    }
  }
}
