package crux.ir;

import crux.ast.SymbolTable.Symbol;
import crux.ast.*;
import crux.ast.OpExpr.Operation;
import crux.ast.traversal.NodeVisitor;
import crux.ast.types.*;
import crux.ir.insts.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class InstPair {
  //a string of Instructions treated as a single unit.
  private Instruction start;
  private Instruction end;
  //the variable that holds the value of an expression
  private Variable value;
  //
  private Boolean isGlobal;
  //Constructors
  //One that takes all three.
  public InstPair(Instruction start, Instruction end, Variable value, Boolean isGlobal){
    this.start = start;
    this.end = end;
    this.value = value;
    this.isGlobal = isGlobal;
  }
  //One that takes only one Instruction but assigns it to both start and end.
  public InstPair(Instruction instruction, Variable value, Boolean isGlobal){
    this.start = instruction;
    this.end= instruction;
    this.value = value;
    this.isGlobal = isGlobal;
  }
  //A variation of both that automatically assigns null to value.
  public InstPair(Instruction start, Instruction end){
    this.start = start;
    this.end = end;
    this.value = null;
    this.isGlobal = false;
  }
  public InstPair(Variable value, Boolean isGlobal){
    this.start = new NopInst();
    this.end = new NopInst();
    this.value = value;
    this.isGlobal = isGlobal;
  }
  public InstPair(){
    this.start = new NopInst();
    this.end = new NopInst();
    this.value = null;
    this.isGlobal = false;
  }
  //get functions
  public Instruction getStart(){
    return this.start;
  }
  public Instruction getEnd(){
    return this.end;
  }
  public Variable getValue(){
    return this.value;
  }
  public Boolean getIsGlobal(){ return this.isGlobal;}
  public boolean isNull(){
    return this.start.getClass()== NopInst.class
            && this.end.getClass()== NopInst.class
            && this.value ==null;
  }
}


/**
 * Convert AST to IR and build the CFG
 */
public final class ASTLower implements NodeVisitor<InstPair> {
  private Program mCurrentProgram = null;
  //The Function currently being traversed.
  private Function mCurrentFunction = null;
  // Maps the symbol of local variables to the LocalVar.
  private Map<Symbol, LocalVar> mCurrentLocalVarMap = null;

  /**
   * A constructor to initialize member variables
   */
  public ASTLower() {}

  public Program lower(DeclarationList ast) {
    visit(ast);
    return mCurrentProgram;
  }

  @Override
  public InstPair visit(DeclarationList declarationList) {
    // create new instance of Program class for mCurrentProgram
    mCurrentProgram = new Program();
    // for each declaration: visit each declaration
    List<Node> decls = declarationList.getChildren();
    for(Node n: decls ){
      n.accept(this);
    }
    // return null
    return new InstPair();
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {
    // create function instance
    Symbol funcSymbol = functionDefinition.getSymbol();
    mCurrentFunction = new Function(funcSymbol.getName(), (FuncType)funcSymbol.getType());
    // create new hashmap<Symbol, Variable> for mCurrentLocalVarMap
    mCurrentLocalVarMap = new HashMap<>();
    // for each argument:
    // 	create LocalVar using mCurrentFunction.getTempVar() and put them in a list
    // 	put the variable (↑) to mCurrentLocalVarMap with correct symbol
    List<LocalVar> localVars = new ArrayList<>();
    List<Symbol> parameters = functionDefinition.getParameters();
    for(Symbol paras: parameters){
      LocalVar v = mCurrentFunction.getTempVar(paras.getType());
      localVars.add(v);
      mCurrentLocalVarMap.put(paras, v);
    }
    // set arguments for mCurrentFunction
    mCurrentFunction.setArguments(localVars);
    //add mCurrentFunction to the function list in mCurrentProgram
    mCurrentProgram.addFunction(mCurrentFunction);
    // visit function body
    // set the start node of mCurrentFunction
    StatementList statementLists = functionDefinition.getStatements();
    InstPair pair = statementLists.accept(this);
    mCurrentFunction.setStart(pair.getStart());
    // dump mCurrentFunction and mCurrentLocalVarMap
    mCurrentFunction = null;
    mCurrentLocalVarMap.clear();
    // return null
    return new InstPair();
  }

  @Override
  public InstPair visit(StatementList statementList) {
    // start with NopInst
    NopInst start = new NopInst();
    // for each statement:
    List<Node> statements = statementList.getChildren();
    Instruction end = new NopInst();
    for(Node node: statements){
      //visit each statement and connect them
      InstPair statement = node.accept(this);
      if(end.getClass() == NopInst.class){
        start.setNext(0, statement.getStart());
      }else{
        end.setNext(0, statement.getStart());
      }
      end = statement.getEnd();
    }
    // return InstPair with start and end of statementList
    // no value for InstPair
    return new InstPair(start, end);
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    Symbol symbol = variableDeclaration.getSymbol();
    //If mCurrentFunction is null, this is a global variable. Add a GlobalDecl to mCurrentProgram.
    if(mCurrentFunction==null){
      GlobalDecl gd = new GlobalDecl(symbol, IntegerConstant.get(mCurrentProgram, 1));
      mCurrentProgram.addGlobalVar(gd);
      return new InstPair();
    }else {
      //Otherwise, it is a local variable. Allocate a temp var and add it to mCurrentLocalVarMap.
      LocalVar v = mCurrentFunction.getTempVar(symbol.getType());
      mCurrentLocalVarMap.put(symbol, v);
      //No instructions need to be done. Return an InstPair of a NopInst if you don’t want
      //to do null checks in visit(StatmentList).
      return new InstPair(v, false);
    }
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    Symbol symbol = arrayDeclaration.getSymbol();
    ArrayType arrayType = (ArrayType)symbol.getType();
    //All array declarations are global. Create and add a GlobalDecl to mCurrentProgram.
    GlobalDecl gd = new GlobalDecl(symbol, IntegerConstant.get(mCurrentProgram, arrayType.getExtent()));
    mCurrentProgram.addGlobalVar(gd);
    return new InstPair();
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    Symbol symbol = name.getSymbol();
    //If the symbol is in mCurrentLocalVarMap,
    if(mCurrentLocalVarMap.containsKey(symbol)){
      //it is a local variable/parameter.
      //Return the LocalVar from the map (no instructions necessary).
      LocalVar v = mCurrentLocalVarMap.get(symbol);
      return new InstPair(v, false);
    }else{
      //Otherwise create a temp AddressVar and AddressAt instruction to
      //store the address to the global variable.
      //offset=0 for non-arrays
      AddressVar addressVar = mCurrentFunction.getTempAddressVar(symbol.getType());
      AddressAt addressAt = new AddressAt(addressVar, symbol);
      LocalVar localVar = mCurrentFunction.getTempVar(symbol.getType());
      LoadInst loadInst = new LoadInst(localVar, addressAt.getDst());
      addressAt.setNext(0,loadInst);
      return new InstPair(addressAt, loadInst, loadInst.getDst(), true);
    }

  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    //Visit the lhs and rhs expressions.
    Expression location = assignment.getLocation();
    Expression value = assignment.getValue();
    InstPair valPair = value.accept(this);
    Instruction end = new NopInst();
    if(assignment.getLocation().getClass() == VarAccess.class){
      VarAccess locVarAccess = (VarAccess)assignment.getLocation();
      Symbol symbol = locVarAccess.getSymbol();
      if(mCurrentLocalVarMap.containsKey(symbol)){
        //If the location is a VarAccess to a LocalVar, copy the value to it.
        InstPair locPair = location.accept(this);
        end.setNext(0, locPair.getStart());
        end = locPair.getEnd();
        end.setNext(0, valPair.getStart());
        end = valPair.getEnd();
        CopyInst copyInst = new CopyInst((LocalVar)locPair.getValue(), valPair.getValue());
        end.setNext(0, copyInst);
        end = copyInst;
        return new InstPair(locPair.getStart(), end);
      }else{
        AddressVar addressVar = mCurrentFunction.getTempAddressVar(symbol.getType());
        AddressAt addressAt = new AddressAt(addressVar, symbol);
        end.setNext(0, addressAt);
        end = addressAt;
        end.setNext(0, valPair.getStart());
        end = valPair.getEnd();
        StoreInst storeInst = new StoreInst((LocalVar)valPair.getValue(), addressAt.getDst());
        end.setNext(0, storeInst);
        end = storeInst;
        return new InstPair(addressAt, end);
      }
    }
        return new InstPair();

  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {
    List<Expression> arguments = call.getArguments();
    List<LocalVar> params = new ArrayList<>();
    Instruction start = new NopInst();
    Instruction end = new NopInst();
    for(Expression expression: arguments){
      //Visit each argument to construct its CFG and add a localVar containing
      //the argument value to the param list.
      InstPair instPair = expression.accept(this);

      params.add((LocalVar)instPair.getValue());

      if(start.getClass() == NopInst.class){
        start = instPair.getStart();
      }else{
        end.setNext(0,instPair.getStart());
      }
      end = instPair.getEnd();
    }
    Symbol callee = call.getCallee();
    //If function is not void, create a temp var for the return value and pass
    //that as the InstPair’s value.
    LocalVar destVar = null;
    if(callee.getType().getClass() != VoidType.class){
      destVar =  mCurrentFunction.getTempVar(call.getType());
    }
    //Construct CallInst with the function symbol.
    if(destVar!=null) {
      CallInst callInst = new CallInst(destVar, callee, params);
      end.setNext(0, callInst);
      end = callInst;
      return new InstPair(start, end, destVar, false);
    }else{
      CallInst callInst = new CallInst(callee, params);
      end.setNext(0, callInst);
      end = callInst;
      return new InstPair(start,end);
    }
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  private BinaryOperator.Op getBinaryOp(Operation op){
    if(op==Operation.ADD){
      return BinaryOperator.Op.Add;
    }else if(op == Operation.SUB){
      return BinaryOperator.Op.Sub;
    }else if(op == Operation.MULT){
      return BinaryOperator.Op.Mul;
    }
    return BinaryOperator.Op.Div;
  }
  private CompareInst.Predicate getCompareOp(Operation op){
    //For GE, GT, LE, LT, EQ, NE, use CompareInst.
    if(op==Operation.GE){
      return CompareInst.Predicate.GE;
    }else if(op == Operation.GT){
      return CompareInst.Predicate.GT;
    }else if(op == Operation.LE){
      return CompareInst.Predicate.LE;
    }else if(op == Operation.LT){
      return CompareInst.Predicate.LT;
    }else if(op == Operation.EQ){
      return CompareInst.Predicate.EQ;
    }
    return CompareInst.Predicate.NE;
  }
  @Override
  public InstPair visit(OpExpr operation) {
    //Visit operands.
    Expression left = operation.getLeft();
    InstPair leftPair = left.accept(this);


    Operation op = operation.getOp();
    Instruction start = leftPair.getStart();
    Instruction end = leftPair.getEnd();

    LocalVar destVar = mCurrentFunction.getTempVar(leftPair.getValue().getType());
    if(operation.getRight()!=null) {
      Expression right = operation.getRight();
      InstPair rightPair = right.accept(this);
      //connect to right operator
      end.setNext(0, rightPair.getStart());
      end = rightPair.getEnd();
      if (op == Operation.ADD || op == Operation.SUB || op == Operation.MULT || op == Operation.DIV) {
        ///For ADD, SUB, MUL, DIV, use BinaryOperator.
        BinaryOperator.Op bop = getBinaryOp(op);
        BinaryOperator binaryOperator = new BinaryOperator(bop, destVar, (LocalVar) leftPair.getValue(), (LocalVar) rightPair.getValue());
        end.setNext(0, binaryOperator);
        end = binaryOperator;
        destVar = binaryOperator.getDst();
      } else if (op == Operation.GE || op == Operation.GT || op == Operation.LE || op == Operation.LT || op == Operation.EQ || op == Operation.NE) {
        //For GE, GT, LE, LT, EQ, NE, use CompareInst.
        CompareInst.Predicate cop = getCompareOp(op);
        CompareInst compareInst = new CompareInst(destVar, cop, (LocalVar) leftPair.getValue(), (LocalVar) rightPair.getValue());
        end.setNext(0, compareInst);
        end = compareInst;
        destVar = compareInst.getDst();
      } else if (op == Operation.LOGIC_NOT) {
          //For LOGIC_NOT, use UnaryNotInst.
          UnaryNotInst unaryNotInst = new UnaryNotInst(destVar, (LocalVar) leftPair.getValue());
          end.setNext(0, unaryNotInst);
          end = unaryNotInst;
          destVar = unaryNotInst.getDst();
      } else if (op == Operation.LOGIC_AND){
        //Boolean Operators
        JumpInst jumpInst = new JumpInst((LocalVar) leftPair.getValue());
      }

    }
    //Create temp var to store result.
    return new InstPair(start, end, destVar, false);
  }

  private InstPair visit(Expression expression) {
    return null;
  }

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    //Visit the index.
    Expression index = access.getIndex();
    InstPair indexPair = index.accept(this);
    //Create a temp AddressVar and AddressAt to store the address to the global variable.
    Symbol symbol = access.getBase();
    //For arrays, use the base type
    ArrayType arrayType = (ArrayType)symbol.getType();
    AddressVar addressVar = mCurrentFunction.getTempAddressVar(arrayType.getBase());
    //do the load
    LocalVar localVar = mCurrentFunction.getTempVar(arrayType.getBase());
    LoadInst loadInst = new LoadInst(localVar, addressVar);
    AddressAt addressAt = new AddressAt(loadInst.getSrcAddress(), symbol);
    Instruction start = indexPair.getStart();
    Instruction end = indexPair.getEnd();
    end.setNext(0, addressAt);
    end = addressAt;
    end.setNext(0, loadInst);
    end = loadInst;
    return new InstPair(start, end, loadInst.getDst(), true);//TODO: NOT SURE WHETHER THIS IS THE CORRECT VALUE
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    boolean value = literalBool.getValue();
    BooleanConstant booleanConstant = BooleanConstant.get(mCurrentProgram, value);
    LocalVar localVar = mCurrentFunction.getTempVar(new BoolType());
    CopyInst copyInst = new CopyInst(localVar, booleanConstant);
    return new InstPair(copyInst, localVar, false);
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    long value = literalInt.getValue();
    IntegerConstant integerConstant = IntegerConstant.get(mCurrentProgram, value);
    LocalVar localVar = mCurrentFunction.getTempVar(new BoolType());
    CopyInst copyInst = new CopyInst(localVar, integerConstant);
    return new InstPair(copyInst, localVar, false);
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    //Visit the return expression.
    Expression value = ret.getValue();
    InstPair retPair = value.accept(this);
    //Pass its value into a ReturnInst.
    ReturnInst returnInst = new ReturnInst((LocalVar)retPair.getValue());
    Instruction start = retPair.getStart();
    Instruction end = retPair.getEnd();
    end.setNext(0, returnInst);
    end = returnInst;
    return new InstPair(start, end);
  }

  /**
   * Break Node
   */
  @Override
  public InstPair visit(Break brk) {
    return null;
  }

  /**
   * Continue Node
   */
  @Override
  public InstPair visit(Continue cont) {
    return null;
  }

  /**
   * Implement If Then Else statements.
   */
  @Override
  public InstPair visit(IfElseBranch ifElseBranch) {
    // Visit condition.
    Expression condition = ifElseBranch.getCondition();
    InstPair condPair = condition.accept(this);
    Instruction start = condPair.getStart();
    Instruction end = condPair.getEnd();
    JumpInst jumpInst = new JumpInst((LocalVar)condPair.getValue());
    end.setNext(0, jumpInst);
    end = jumpInst;
    // Use JumpInst.
    // Visit thenBlock and elseBlock.
    StatementList thenBlock = ifElseBranch.getThenBlock();
    StatementList elseBlock = ifElseBranch.getElseBlock();
    InstPair thenPair = thenBlock.accept(this);
    InstPair elsePair = elseBlock.accept(this);
    end.setNext(0, elsePair.getStart());
    end.setNext(1, thenPair.getStart());
    // Merge the blocks into a NopInst.
    end = elsePair.getEnd();
    NopInst nop = new NopInst();
    end.setNext(0, nop);
    end = thenPair.getEnd();
    end.setNext(0, nop);//TODO:should here be 0 or 1
    end = nop;
    return new InstPair(start, end);
  }

  /**
   * Implement loops.
   */
  @Override
  public InstPair visit(Loop loop) {
    // Visit the loop header
    StatementList body = loop.getBody();
    InstPair bodyPair = body.accept(this);
    Instruction header = bodyPair.getStart();
    // Create a NopInst as the loop exit,
    //and connect header to exit. Store
    //the exit in a global variable ( since loops
    //can be nested, outer loop exits need to be remembered in some
    //way ) , so that break statements in the
    //loop can find the exit.
    // Visit the loop body , and add edge
    //from header to body
    // Add two edges from the body, one
    //to the loop header and one to the
    //loop exit
    // Remove the current loop exit
    return null;
  }
}
