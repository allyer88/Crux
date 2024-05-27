package crux.backend;

import crux.ast.SymbolTable.Symbol;
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

  private void visitBody(Instruction i){
    //Linearize CFG using jumps and labels
    //Use DFS traversal
    //Refer to Function.assignLabels(int count[])
    if(i==null){
      return;
    }
    i.accept(this);
    i = i.getNext(0);
    if(i==null){
      i = i.getNext(1);
    }
    visitBody(i);
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
    visitBody(i);
    //Print epilogue
    out.printCode("movq $0, %rax");
    out.printCode("leave");
    out.printCode("ret");
  }

  public void visit(AddressAt i) {}

  public void visit(BinaryOperator i) {}

  public void visit(CompareInst i) {}

  public void visit(CopyInst i) {}

  public void visit(JumpInst i) {}

  public void visit(LoadInst i) {}

  public void visit(NopInst i) {}

  public void visit(StoreInst i) {}

  public void visit(ReturnInst i) {}

  public void visit(CallInst i) {}

  public void visit(UnaryNotInst i) {}
}
