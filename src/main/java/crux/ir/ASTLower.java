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
  Instruction start;
  Instruction end;
  //the variable that holds the value of an expression
  LocalVar value;
  //Constructors
  //One that takes all three.
  InstPair(Instruction start, Instruction end, LocalVar value){
    this.start = start;
    this.end = end;
    this.value = value;
  }
  //One that takes only one Instruction but assigns it to both start and end.
  InstPair(Instruction instruction, LocalVar value){
    this.start = instruction;
    this.end= instruction;
    this.value = value;
  }
  //A variation of both that automatically assigns null to value.
  InstPair(Instruction start, Instruction end){
    this.start = start;
    this.end = end;
    this.value = null;
  }
  InstPair(LocalVar value){
    this.start = new NopInst();
    this.end = new NopInst();
    this.value = value;
  }
  //get functions
  Instruction getStart(){
    return this.start;
  }
  Instruction getEnd(){
    return this.end;
  }
  LocalVar getValue(){
    return this.value;
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
    return null;
  }

  /**
   * This visitor should create a Function instance for the functionDefinition node, add parameters
   * to the localVarMap, add the function to the program, and init the function start Instruction.
   */
  @Override
  public InstPair visit(FunctionDefinition functionDefinition) {
    List<Symbol> parameters = functionDefinition.getParameters();
    Symbol funcSymbol = functionDefinition.getSymbol();
    //create a Function instance
    Function function = new Function(funcSymbol.getName(), (FuncType)funcSymbol.getType());
    //add parameters to the localVarMap
    for(Symbol paras: parameters){
      LocalVar v = function.getTempVar(paras.getType(), "$");
      mCurrentLocalVarMap.put(paras, v);
    }
    Instruction start;
    Instruction end;
    //add the function to the program

    //init the function start Instruction
    return null;
  }

  @Override
  public InstPair visit(StatementList statementList) {
    return null;
  }

  /**
   * Declarations, could be either local or Global
   */
  @Override
  public InstPair visit(VariableDeclaration variableDeclaration) {
    return null;
  }

  /**
   * Create a declaration for array and connected it to the CFG
   */
  @Override
  public InstPair visit(ArrayDeclaration arrayDeclaration) {
    return null;
  }

  /**
   * LookUp the name in the map(s). For globals, we should do a load to get the value to load into a
   * LocalVar.
   */
  @Override
  public InstPair visit(VarAccess name) {
    return null;
  }

  /**
   * If the location is a VarAccess to a LocalVar, copy the value to it. If the location is a
   * VarAccess to a global, store the value. If the location is ArrayAccess, store the value.
   */
  @Override
  public InstPair visit(Assignment assignment) {
    return null;
  }

  /**
   * Lower a Call.
   */
  @Override
  public InstPair visit(Call call) {
    return null;
  }

  /**
   * Handle operations like arithmetics and comparisons. Also handle logical operations (and,
   * or, not).
   */
  @Override
  public InstPair visit(OpExpr operation) {
    return null;
  }

  private InstPair visit(Expression expression) {
    return null;
  }

  /**
   * It should compute the address into the array, do the load, and return the value in a LocalVar.
   */
  @Override
  public InstPair visit(ArrayAccess access) {
    return null;
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralBool literalBool) {
    return null;
  }

  /**
   * Copy the literal into a tempVar
   */
  @Override
  public InstPair visit(LiteralInt literalInt) {
    return null;
  }

  /**
   * Lower a Return.
   */
  @Override
  public InstPair visit(Return ret) {
    return null;
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
    return null;
  }

  /**
   * Implement loops.
   */
  @Override
  public InstPair visit(Loop loop) {
    return null;
  }
}
