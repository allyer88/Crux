package crux.ast.types;

/**
 * Types for Integers values. This should implement the equivalent methods along with add, sub, mul,
 * div, and compare. The method equivalent will check if the param is an instance of IntType.
 */
public final class IntType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;

  @Override
  public String toString() {
    return "int";
  }

  @Override
  public boolean equivalent(Type that){
    return this.toString().equals(that.toString());
  }
  @Override
  public Type add(Type that){
    if(this.equivalent(that)){
      return new IntType();
    }else{
      return super.add(that);
    }
  }
  @Override
  public Type sub(Type that){
    if(this.equivalent(that)){
      return new IntType();
    }else{
      return super.sub(that);
    }
  }
  @Override
  public Type mul(Type that){
    if(this.equivalent(that)){
      return new IntType();
    }else{
      return super.mul(that);
    }
  }
  @Override
  public Type div(Type that){
    if(this.equivalent(that)){
      return new IntType();
    }else{
      return super.div(that);
    }
  }
  @Override
  public Type compare(Type that){
    if(this.equivalent(that)){
      return new BoolType();
    }else{
      return super.compare(that);
    }
  }

  @Override
  public Type assign(Type that){
    if(this.equivalent(that)){
      return new IntType();
    }else{
      return super.assign(that);
    }
  }
}
