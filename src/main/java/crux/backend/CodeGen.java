package crux.backend;

import crux.ast.SymbolTable.Symbol;
import crux.ast.types.*;
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
  private List<String> argReg = Arrays.asList("%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9");

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
      out.printCode(".comm " + name + ", " + size + ", 8" );
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
    //Keep track of instructions to visit in a stack (like a DFS)
    //Keep track of visited instructions in a set to avoid redundant visits
    //If the instruction needs a label, print the label first
    //Visit current instruction on stack, then push the next instructions onto stack
    while(i!=null){
      //If instruction has already been visited, jmp to its label instead.
      if(InstMap.containsKey(i)) {
        out.printCode("jmp " + InstMap.get(i));
      }else {
          i.accept(this);
      }
      i = i.getNext(0);
      visitBody(i, f ,count);
      if(i!=null){
        i = i.getNext(1);
      }
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
    out.printCode("movq "+ name+ "@GOTPCREL(%rip) , %r11");
    if(varOffset!=null){
      int offsetCount = getStackSlot(varOffset);
      int stackOffset = -offsetCount*8;
      out.printCode("movq " + stackOffset + "(%rbp), %r10" );// Load offset
      out.printCode("imulq $8, %r10");// Multiply offset by 8
      out.printCode("addq %r10, %r11"); // Add array base address with offset
    }
    AddressVar dst = i.getDst();
    int dstCount = getStackSlot(dst);
    int dstOffset = -dstCount*8;
    out.printCode("movq %r11, " + dstOffset + "(%rbp)");
  }

  public void visit(BinaryOperator i) {
    out.printCode("/* BinaryOperator */");
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
    out.printCode("/* CompareInst */");
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
    if(i.getSrcValue().getClass() == IntegerConstant.class){
      IntegerConstant scr = (IntegerConstant)i.getSrcValue();
      out.printCode("movq $" + scr.getValue() + ", %r10" );
    }else if(i.getSrcValue().getClass() == BooleanConstant.class){
      BooleanConstant scr = (BooleanConstant)i.getSrcValue();
      int bool=0;
      if(scr.getValue()){
        bool =1;
      }
      out.printCode("movq $" + bool + ", %r10" );
    }else{
      Variable src = (Variable)i.getSrcValue();
      int srcslot = getStackSlot(src);
      int srcoffset = -srcslot * 8;
      out.printCode("movq " + srcoffset + "(%rbp)" + ", %r10" );
    }
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    out.printCode("movq %r10, "+ dstoffset+"(%rbp)");
  }

  public void visit(JumpInst i) {
    out.printCode("/* JumpInst */");
    LocalVar pred = i.getPredicate();
    int predslot = getStackSlot(pred);
    int predoffset = -predslot * 8;
    out.printCode("movq " + predoffset + "(%rbp), " + "%r10");
    if(InstMap.get(i)!=null){
      out.printCode("cmp $1, %r10");
      String trueLabel = InstMap.get(i);
      out.printCode("je " + trueLabel);
    }
  }

  public void visit(LoadInst i) {
    out.printCode("/* LoadInst */");
    LocalVar dst  = i.getDst();
    AddressVar scr = i.getSrcAddress();
    int scrslot = getStackSlot(scr);
    int scroffset = -scrslot * 8;
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    out.printCode("movq " + scroffset +"(%rbp), %r10");
    out.printCode("movq 0(%r10), %r11");
    out.printCode("movq %r11, " + dstoffset + "(%rbp)");
  }

  public void visit(NopInst i) {
    out.printCode("/* Nop */");
  }

  public void visit(StoreInst i) {
    out.printCode("/* StoreInst */");
    LocalVar scr  = i.getSrcValue();
    AddressVar dst = i.getDestAddress();
    int scrslot = getStackSlot(scr);
    int scroffset = -scrslot * 8;
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    out.printCode("movq " + scroffset +"(%rbp), %r10");
    out.printCode("movq " + dstoffset + "(%rbp), %r11");
    out.printCode("movq %r10, 0(%r11)");
  }

  public void visit(ReturnInst i) {
    out.printCode("/* ReturnInst */");
    LocalVar returnValue = i.getReturnValue();
    int retslot = getStackSlot(returnValue);
    int retoffset = -retslot * 8;
    //we need to make sure, that the value we want to return is in register %rax
    // and then return from the function
    out.printCode("movq " + retoffset + "(%rbp), %rax");
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(CallInst i) {
    out.printCode("/* CallInst */");
    //movq all of the arguments to their correct locations (see Slide 13).
    List<LocalVar> params =  i.getParams();
    for (int j=0; j< params.size();j++){
      int slot = getStackSlot(params.get(j));
      int offset = -slot * 8;
      if(j<6){
        out.printCode("movq " + offset+ "(%rbp), " + argReg.get(j));
      }else{
        int argRegOffset = 16 * (j-5);
        out.printCode("movq "+  offset+ "(%rbp), " + argRegOffset + "(%rbp)");
      }
    }
    //call func
    //func is the label of the function.
    Symbol symbol = i.getCallee();
    String func = symbol.getName();
    out.printCode("call "+ func);
    //If the function is not void, the return value is in %rax and you should movq it into the stack.
    FuncType funcType = (FuncType) symbol.getType();
    if(funcType.getRet().getClass() != VoidType.class){
      LocalVar dst = i.getDst();
      int slot = getStackSlot(dst);
      int offset = -slot * 8;
      out.printCode("movq %rax, "+offset+"(%rbp)");
    }
  }

  public void visit(UnaryNotInst i) {
    out.printCode("/* UnaryNotInst */");
    LocalVar inner = i.getInner();
    LocalVar dst = i.getDst();
    int innerslot = getStackSlot(inner);
    int inneroffset = -innerslot * 8;
    int dstslot = getStackSlot(dst);
    int dstoffset = -dstslot * 8;
    out.printCode("movq $1, %r11");
    out.printCode("subq %r11, " + inneroffset+ "(%rbp)");
    out.printCode("movq "+  inneroffset + "(%rbp)" + dstoffset + "(%rbp)");
  }
}
