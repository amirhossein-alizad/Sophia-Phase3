package main.ast.nodes.expression;


import main.ast.nodes.Node;
import main.ast.types.NoType;
import main.ast.types.NullType;
import main.ast.types.Type;
import main.ast.types.single.BoolType;
import main.ast.types.single.IntType;
import main.ast.types.single.StringType;

public abstract class Expression extends Node {
    public static Type exprType = null;
    public void setType(Type type){this.exprType = type;}
    public Type getType(){return this.exprType;}
}
