package crux.backend;

import crux.ast.SymbolTable.Symbol;
import crux.ast.types.ArrayType;
import crux.ast.types.BoolType;
import crux.ast.types.IntType;
import crux.ast.types.Type;
import crux.ir.*;
import crux.ir.insts.*;
import crux.printing.IRValueFormatter;

import java.util.*;


/**
 * Convert the CFG into Assembly Instructions
 */
public final class CodeGen extends InstVisitor {
  private final Program p;
  private final CodePrinter out;

  public CodeGen(Program p) {
    this.p = p;
    // Do not change the file name that is outputted or it will
    // break the grader!

    out = new CodePrinter("a.s");
  }

  /**
   * It should allocate space for globals call genCode for each Function
   */
  private long getSize(IntegerConstant i, Type t){
    if(t.getClass()== IntType.class){
      return i.getValue()*4;
    }
    //t.getClass() == BoolType.class
    return i.getValue();//*1byte
  }
  public void genCode() {
    for (Iterator<GlobalDecl> glob_it = p.getGlobals(); glob_it.hasNext();){
      GlobalDecl g = glob_it.next();
      Symbol symbol = g.getSymbol();
      String name = symbol.getName();
      long size = getSize(g.getNumElement(), symbol.getType());
      out.printCode(".comm" + name + ", " + size + ", 8" );
    }
    int count[] = new int[1];
    for(Iterator<Function> fun_it = p.getFunctions(); fun_it.hasNext();){
      Function f = fun_it.next();
      genCode(f, count);
    }
    out.close();
  }
  HashMap<Variable, Integer> varMap = new HashMap<Variable, Integer>();
  int varcount = 0;

  public int numSlots() {
    return varcount;
  }
  Integer getStackSlot(Variable v) {
    if (!varMap.containsKey(v)) {
      varcount++;
      varMap.put(v, varcount);
    }
    return varMap.get(v);
  }
  HashMap<Instruction, String> InstMap =new HashMap<Instruction, String>();

  private void visitBody(Instruction i, Function f, int count[]){
    //Linearize CFG using jumps and labels
    //Use DFS traversal
    //Refer to Function.assignLabels(int count[])
    //Keep track of visited instructions in a set to avoid redundant visits
    while(i!=null){
      if(InstMap.containsKey(i)){
        out.printCode("jmp " + InstMap.get(i));
      }else if(i.getClass() == JumpInst.class){
        HashMap<Instruction, String> var = f.assignLabels(count);
        InstMap.putAll(var);
        i.accept(this);
      }else {
        i.accept(this);
      }
      i = i.getNext(0);
      visitBody(i,f ,count);
      i = i.getNext(1);
    }

  }
  private void genCode(Function f, int count[]){
    //Assign labels to jump targets f.assignLabels(count);
    //then store it in a global variable
    HashMap<Instruction, String> var = f.assignLabels(count);
    InstMap.putAll(var);
    out.printCode(".globl " + f.getName());
    out.printLabel(f.getName() + ":");
    //Print prologue such that stack is 16 byte aligned
    int numSlots =  f.getNumTempVars()+f.getNumTempAddressVars();
    numSlots = (numSlots + 1) & ~1; //round up to nearest even number
    out.printCode("enter $(8 * " + numSlots + "), $0");
    //Move arguments from registers to local variable
    List<String> argReg = Arrays.asList("%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9");
    List<LocalVar> args= f.getArguments();
    for(int i=0;i<args.size();i++){
      int offset;
      int varSlots = getStackSlot(args.get(i));
      if(i<6){
        offset = -varSlots * 8;
        out.printCode("movq "+ argReg.get(i) +", " + offset + "(%rbp)");
      }else{
        int argRegOffset = 16 * (i-5);
        offset = -varSlots * 8;
        out.printCode("movq "+ argRegOffset + "(%rbp), " + offset + "(%rbp)");
      }
    }
    //Generate code for function body
    Instruction i = f.getStart();
    visitBody(i,f, count);
    //Print epilogue
    out.printCode("movq $0, %rax");
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(AddressAt i) {
    out.printCode("/* AddressAt */");
    LocalVar varOffset = i.getOffset();
    Symbol symbol = i.getBase();
    String name = symbol.getName();
    int varCount = getStackSlot(i.getDst());
    int varStackOffset = -varCount*8;
    out.printCode("movq "+ name+ "@GOTPCREL(%rip) , %r11");
    out.printCode("movq %r11, " + varStackOffset + "(%rbp)");
    if(varOffset!=null){
      int offsetCount = getStackSlot(varOffset);
      int stackOffset = -offsetCount*8;
      out.printCode("movq " + stackOffset + "(%rbp), %r11" );// Load offset
      out.printCode("movq $8, %r10");
      out.printCode("imul %r10, %r11");// Multiply offset by 8

      movq Data@GOTPCREL(%rip) , %r10     // Load array address
      addq %r10, %r11                     // Add array base address with offset
      movq %r11, -368(%rbp)
    }
  }

  public void visit(BinaryOperator i) {
    Variable dst = i.getDst();
    Variable lhs = i.getLeftOperand();
    Variable rhs = i.getRightOperand();
    int dstslot = getStackSlot(dst);
    int lhsslot = getStackSlot(lhs);
    int rhsslot = getStackSlot(rhs);
    int dstoffset = -dstslot * 8;
    int lhsoffset = -lhsslot * 8;
    int rhsoffset = -rhsslot * 8;
    out.printCode("movq "+lhsoffset+"(%rbp), %r10");
    switch(i.getOperator()) {
      case Add:
        out.printCode("addq "+rhsoffset+"(%rbp), %r10");
        out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
        break;
      case Sub:
        out.printCode("subq "+rhsoffset+"(%rbp), %r10");
        out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
        break;
      case Mul:
        out.printCode("imulq "+rhsoffset+"(%rbp), %r10");
        out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
        break;
      case Div:
        out.printCode("cqto");
        out.printCode("idivq " + rhsoffset + "(%rbp)");
        out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
        break;
    }
  }

  public void visit(CompareInst i) {
    Variable dst = i.getDst();
    Variable lhs = i.getLeftOperand();
    Variable rhs = i.getRightOperand();
    int dstslot = getStackSlot(dst);
    int lhsslot = getStackSlot(lhs);
    int rhsslot = getStackSlot(rhs);
    int dstoffset = -dstslot * 8;
    int lhsoffset = -lhsslot * 8;
    int rhsoffset = -rhsslot * 8;
    out.printCode("movq $0, %rax");//false
    out.printCode("movq $1, %r10");//true
    out.printCode("movq "+lhsoffset+"(%rbp), %r11");
    out.printCode("cmp "+rhsoffset+"(%rbp), %r11"); //lfs-rhs
    switch(i.getPredicate()){
      case GE:
        out.printCode("cmovge %r10, %rax");
        break;
      case GT:
        out.printCode("cmovg %r10, %rax");
        break;
      case LE:
        out.printCode("cmovle %r10, %rax");
        break;
      case LT:
        out.printCode("cmovl %r10, %rax");
        break;
      case NE:
        out.printCode("cmovne %r10, %rax");
        break;
      case EQ:
        out.printCode("cmove %r10, %rax");
        break;
    }
    out.printCode("movq %rax, "+ dstoffset+"(%rbp)");
  }

  public void visit(CopyInst i) {
    out.printCode("/* CopyInst */");
    Variable dst = i.getDstVar();
    Value scr = i.getSrcValue();
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    out.printCode("movq " + scr + "%r10" );
    out.printCode("movq "+dstoffset+"(%rbp), %r11");

  }

  public void visit(JumpInst i) {}

  public void visit(LoadInst i) {}

  public void visit(NopInst i) {
    out.printCode("/* Nop */");
  }

  public void visit(StoreInst i) {}

  public void visit(ReturnInst i) {
    LocalVar returnValue = i.getReturnValue();
    int retslot = getStackSlot(returnValue);
    int retoffset = -retslot * 8;
    //we need to make sure, that the value we want to return is in register %rax
    // and then return from the function
    out.printCode("movq " + retoffset + "(%rbp), %rax");
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(CallInst i) {}

  public void visit(UnaryNotInst i) {}
}
