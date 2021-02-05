package main.visitor.typeChecker;

import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.ConstructorDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.MethodDeclaration;
import main.ast.nodes.declaration.variableDec.VarDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.ListValue;
import main.ast.nodes.expression.values.NullValue;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.nodes.expression.values.primitive.StringValue;
import main.ast.types.NoType;
import main.ast.types.NullType;
import main.ast.types.Type;
import main.ast.types.functionPointer.FptrType;
import main.ast.types.list.ListNameType;
import main.ast.types.list.ListType;
import main.ast.types.single.BoolType;
import main.ast.types.single.ClassType;
import main.ast.types.single.IntType;
import main.ast.types.single.StringType;
import main.compileErrorException.typeErrors.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.ClassSymbolTableItem;
import main.symbolTable.items.FieldSymbolTableItem;
import main.symbolTable.items.LocalVariableSymbolTableItem;
import main.symbolTable.items.MethodSymbolTableItem;
import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;
import java.util.ArrayList;


public class ExpressionTypeChecker extends Visitor<Type> {
    private final Graph<String> classHierarchy;
    private ClassDeclaration currentClassName;
    private MethodDeclaration currentMethodName;
    private int err = 0;
    private boolean lvalue = false;
    private boolean method_statement = false;

    public ExpressionTypeChecker(Graph<String> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }
    public void setCurrentClassName(ClassDeclaration classDeclaration) {
        this.currentClassName = classDeclaration;
    }
    public void setCurrentMethodName(MethodDeclaration methodDeclaration) {
        this.currentMethodName= methodDeclaration;
    }
    public void setLvalue(boolean val){this.lvalue = val;}
    public boolean getLvalue(){return this.lvalue;}
    public void set_method_statement(boolean val){this.method_statement = val;}
    public boolean get_method_statemen(){return this.method_statement;}
    @Override
    public Type visit(BinaryExpression binaryExpression) {
        this.lvalue = false;
        Type first = binaryExpression.getFirstOperand().accept(this);
        boolean l = this.lvalue;
        this.lvalue = true;
        Type second = binaryExpression.getSecondOperand().accept(this);
        BinaryOperator binary = binaryExpression.getBinaryOperator();
        switch (binary) {
            case add, sub, mult, div, mod -> {
                if ((first instanceof IntType && second instanceof NoType) || (first instanceof NoType && second instanceof IntType) || (first instanceof NoType && second instanceof NoType))
                    return new NoType();
                if ((first instanceof IntType && second instanceof IntType))
                    return new IntType();
                else {
                    UnsupportedOperandType err = new UnsupportedOperandType(binaryExpression.getLine(), binary.toString());
                    binaryExpression.addError(err);
                    return new NoType();
                }
            }
            case assign -> {
                if (l) {
                    binaryExpression.addError(new LeftSideNotLvalue(binaryExpression.getLine()));
                }
                if(first instanceof NoType)
                    return new NoType();
                if (!isSubType(second, first)) {
                    UnsupportedOperandType err = new UnsupportedOperandType(binaryExpression.getLine(), binary.toString());
                    binaryExpression.addError(err);
                    return new NoType();
                } else {
                    if (l)
                        return new NoType();
                    return first;
                }
            }
            case lt, gt -> {
                if ((first instanceof IntType && second instanceof NoType) || (first instanceof NoType && second instanceof IntType) || (first instanceof NoType && second instanceof NoType))
                    return new NoType();
                if (first instanceof IntType && second instanceof IntType)
                    return new BoolType();
                else {
                    UnsupportedOperandType err = new UnsupportedOperandType(binaryExpression.getLine(), binary.toString());
                    binaryExpression.addError(err);
                    return new NoType();
                }
            }
            case and, or -> {
                if ((first instanceof BoolType && second instanceof NoType) || (first instanceof NoType && second instanceof BoolType) || (first instanceof NoType && second instanceof NoType))
                    return new BoolType();
                if (first instanceof BoolType && second instanceof BoolType)
                    return new BoolType();
                else {
                    UnsupportedOperandType err = new UnsupportedOperandType(binaryExpression.getLine(), binary.toString());
                    binaryExpression.addError(err);
                    return new NoType();
                }
            }
            case eq, neq -> {
                if (first instanceof NoType || second instanceof NoType)
                    return new NoType();
                if((first instanceof NullType &&(second instanceof ClassType || second instanceof FptrType))||(second instanceof NullType &&(first instanceof ClassType || first instanceof FptrType)))
                    return new BoolType();
                if (isSubType(first, second) && isSubType(second, first) && (!(first instanceof ListType) && !(second instanceof ListType))) {
                    return new BoolType();
                } else {
                    UnsupportedOperandType err = new UnsupportedOperandType(binaryExpression.getLine(), binary.toString());
                    binaryExpression.addError(err);
                    return new NoType();
                }
            }
        }
        return new BoolType();
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        this.lvalue = false;
        Type operand = unaryExpression.getOperand().accept(this);
        boolean l = this.lvalue;
        this.lvalue = true;
        UnaryOperator operator = unaryExpression.getOperator();
        switch (operator) {
            case minus -> {
                if (operand instanceof IntType)
                    return new IntType();
                if (operand instanceof NoType) {
                } else {
                    UnsupportedOperandType err = new UnsupportedOperandType(unaryExpression.getLine(), operator.toString());
                    unaryExpression.addError(err);
                }
                return new NoType();
            }
            case predec, preinc, postdec, postinc -> {
                if (l) {
                    unaryExpression.addError(new IncDecOperandNotLvalue(unaryExpression.getLine(), operator.toString()));
                }
                if (operand instanceof IntType)
                    return new IntType();
                if (operand instanceof NoType)
                    return new NoType();
                else {
                    UnsupportedOperandType err = new UnsupportedOperandType(unaryExpression.getLine(), operator.toString());
                    unaryExpression.addError(err);
                    return new NoType();
                }
            }
            case not -> {
                if (operand instanceof BoolType)
                    return new BoolType();
                if (operand instanceof NoType) {
                } else {
                    UnsupportedOperandType err = new UnsupportedOperandType(unaryExpression.getLine(), operator.toString());
                    unaryExpression.addError(err);
                }
                return new NoType();
            }
        }
        return null;
    }

    @Override
    public Type visit(ObjectOrListMemberAccess objectOrListMemberAccess) {
        Type instance = objectOrListMemberAccess.getInstance().accept(this);
        String name = objectOrListMemberAccess.getMemberName().getName();
        if(instance instanceof NoType)
            return new NoType();
        if(instance instanceof ListType){
            ListType lst = (ListType) instance;
            for(ListNameType el: lst.getElementsTypes()){
                if(el.getName().getName().equals(name)) {
                    Validate(el.getType());
                    int e = this.err;
                    this.err = 0;
                    if (e > 0)
                        return new NoType();
                    return el.getType();
                }
            }
            objectOrListMemberAccess.addError(new ListMemberNotFound(objectOrListMemberAccess.getLine(), name));
            return new NoType();
        }
        else if(instance instanceof ClassType){
            ClassType cls = (ClassType) instance;
            try {
                ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + cls.getClassName().getName(), true);
                FieldSymbolTableItem fieldSymbolTableItem = (FieldSymbolTableItem) classSymbolTableItem.getClassSymbolTable().getItem(FieldSymbolTableItem.START_KEY + name, true);
                Validate(fieldSymbolTableItem.getType());
                int e = this.err;
                this.err = 0;
                if (e > 0)
                    return new NoType();
                return fieldSymbolTableItem.getType();

            }
            catch(ItemNotFoundException exc){
                try{
                    ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + cls.getClassName().getName(), true);
                    MethodSymbolTableItem methodSymbolTableItem = (MethodSymbolTableItem) classSymbolTableItem.getClassSymbolTable().getItem(MethodSymbolTableItem.START_KEY + name, true);
                    this.lvalue = true;
                    return new FptrType(methodSymbolTableItem.getArgTypes(), methodSymbolTableItem.getReturnType());
                }
                catch (ItemNotFoundException ex){
                    if(cls.getClassName().getName().equals(name)) {
                        try {
                            ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + cls.getClassName().getName(), true);
                            ConstructorDeclaration constructor = classSymbolTableItem.getClassDeclaration().getConstructor();
                            if (constructor != null) {
                                ArrayList<Type> arr = new ArrayList<>();
                                for(VarDeclaration var: constructor.getArgs())
                                    arr.add(var.getType());
                                return new FptrType(arr, new NullType());
                            }
                            else{
                                ArrayList<Type> arr = new ArrayList<>();
                                return new FptrType(arr, new NullType());
                            }
                        }catch (ItemNotFoundException ecx){ }
                    }
                    else{
                        objectOrListMemberAccess.addError(new MemberNotAvailableInClass(objectOrListMemberAccess.getLine(), name, cls.getClassName().getName()));
                        return new NoType();
                    }
                }
            }
        }
        else{
            objectOrListMemberAccess.addError(new MemberAccessOnNoneObjOrListType(objectOrListMemberAccess.getLine()));
            return new NoType();
        }
        return new NoType();
    }

    @Override
    public Type visit(Identifier identifier) {
        try{
            ClassSymbolTableItem childSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY +
                    currentClassName.getClassName().getName(), true);
            SymbolTable childSymbolTable = childSymbolTableItem.getClassSymbolTable();
            MethodSymbolTableItem method = (MethodSymbolTableItem) childSymbolTable.getItem(MethodSymbolTableItem.START_KEY + currentMethodName.getMethodName().getName(), true);
            SymbolTable methodSymbolTable = method.getMethodSymbolTable();
            LocalVariableSymbolTableItem var = (LocalVariableSymbolTableItem) methodSymbolTable.getItem(LocalVariableSymbolTableItem.START_KEY + identifier.getName(), true);
            Validate(var.getType());
            int e = this.err;
            this.err = 0;
            if(e > 0)
                return new NoType();
            return var.getType();
        }
        catch(ItemNotFoundException ex){
                identifier.addError(new VarNotDeclared(identifier.getLine(), identifier.getName()));
                return new NoType();
        }
//        identifier.addError(new VarNotDeclared(identifier.getLine(), identifier.getName()));
//        return new NoType();
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        Type instance = listAccessByIndex.getInstance().accept(this);
        boolean l = this.lvalue;
        Type index = listAccessByIndex.getIndex().accept(this);
        this.lvalue = l;
        boolean not_int = false;
        if(!(index instanceof IntType || index instanceof NoType)){
            listAccessByIndex.addError(new ListIndexNotInt(listAccessByIndex.getLine()));
            not_int = true;
        }
        if(instance instanceof NoType)
            return new NoType();
        if(!(instance instanceof ListType)){
            listAccessByIndex.addError(new ListAccessByIndexOnNoneList(listAccessByIndex.getLine()));
            return new NoType();
        }
        boolean multipleTypes = false;
        Type last = null;
        for(ListNameType arg: ((ListType) instance).getElementsTypes()){
            if(last == null)
                last = arg.getType();
            else{
                if(!isSubType(last, arg.getType())){
                    multipleTypes = true;
                    break;
                }
                last = arg.getType();
            }
        }
        if(multipleTypes && !(listAccessByIndex.getIndex() instanceof IntValue)){
            listAccessByIndex.addError(new CantUseExprAsIndexOfMultiTypeList(listAccessByIndex.getLine()));
            return new NoType();
        }
        if(not_int)
            return new NoType();
        else{
            ListType lst = (ListType) instance;
            int idx;
            if(listAccessByIndex.getIndex() instanceof IntValue){
                idx = ((IntValue) listAccessByIndex.getIndex()).getConstant();
                if(idx >= lst.getElementsTypes().size()){
                    Validate(lst.getElementsTypes().get(0).getType());
                    int e = this.err;
                    this.err = 0;
                    if(e > 0)
                        return new NoType();
                    return lst.getElementsTypes().get(0).getType();
                }
                else{
                    Validate(lst.getElementsTypes().get(idx).getType());
                    int e = this.err;
                    this.err = 0;
                    if(e > 0)
                        return new NoType();
                    return lst.getElementsTypes().get(idx).getType();
                }
            }
            else{
                Validate(lst.getElementsTypes().get(0).getType());
                int e = this.err;
                this.err = 0;
                if(e > 0)
                    return new NoType();
                return lst.getElementsTypes().get(0).getType();
            }
        }
    }

    @Override
    public Type visit(MethodCall methodCall) {
        this.lvalue = true;
        Type instance = methodCall.getInstance().accept(this);
        if(instance instanceof FptrType) {
            ArrayList<Expression> args = methodCall.getArgs();
            ArrayList<Type> argumentTypes = ((FptrType)instance).getArgumentsTypes();
            if(args.size() != argumentTypes.size()){
                methodCall.addError( new MethodCallNotMatchDefinition(methodCall.getLine()));
                for(Expression arg: args)
                    arg.accept(this);
                if(((FptrType)instance).getReturnType() instanceof NullType && !this.method_statement){
                    methodCall.addError(new CantUseValueOfVoidMethod(methodCall.getLine()));
                    return new NoType();
                }
                return new NoType();
            }
            int size = args.size();
            for(int i = 0; i < size; i++){
                if(!isSubType(args.get(i).accept(this), argumentTypes.get(i))){
                    methodCall.addError( new MethodCallNotMatchDefinition(methodCall.getLine()));
                    if(((FptrType)instance).getReturnType() instanceof NullType && !this.method_statement){
                        methodCall.addError(new CantUseValueOfVoidMethod(methodCall.getLine()));
                        return new NoType();
                    }
                    return new NoType();
                }
            }
            Validate(((FptrType)instance).getReturnType());
            int e = this.err;
            this.err=0;
            if(e > 0)
                return new NoType();
            if(((FptrType)instance).getReturnType() instanceof NullType && !this.method_statement){
                methodCall.addError(new CantUseValueOfVoidMethod(methodCall.getLine()));
                return new NoType();
            }
            return ((FptrType)instance).getReturnType();
        }
        else{
            if(!(instance instanceof NoType))
                methodCall.addError(new CallOnNoneFptrType(methodCall.getLine()));
            return new NoType();
        }
    }

    @Override
    public Type visit(NewClassInstance newClassInstance) {
        this.lvalue = true;
        String className = newClassInstance.getClassType().getClassName().getName();
        ArrayList<Expression> args = newClassInstance.getArgs();
        try{
            ClassSymbolTableItem classItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + className, true);
            ConstructorDeclaration constructorDeclaration = classItem.getClassDeclaration().getConstructor();
            if(constructorDeclaration == null){
                if (args.size() > 0){
                    newClassInstance.addError(new ConstructorArgsNotMatchDefinition(newClassInstance));
                }
                return new NoType();
            }
            if(args.size() != constructorDeclaration.getArgs().size()){
                newClassInstance.addError(new ConstructorArgsNotMatchDefinition(newClassInstance));
                return new NoType();
            }
            int i = 0;
            for(VarDeclaration arg: constructorDeclaration.getArgs()){
                if(!isSubType(args.get(i).accept(this), arg.getType())){
                    newClassInstance.addError(new ConstructorArgsNotMatchDefinition(newClassInstance));
                    return new NoType();
                }
                i++;
            }
            return newClassInstance.getClassType();
        }
        catch (ItemNotFoundException exc){
            newClassInstance.addError(new ClassNotDeclared(newClassInstance.getLine(), className));
            return new NoType();
        }
    }

    @Override
    public Type visit(ThisClass thisClass) {
        if(currentClassName != null){
            return new ClassType(currentClassName.getClassName());
        }
        else{
            return new NoType();
        }
    }

    @Override
    public Type visit(ListValue listValue) {
        this.lvalue = true;
        ArrayList<Expression> elements = listValue.getElements();
        ArrayList<ListNameType> el = new ArrayList<>();
        if(elements.size() > 0){
            Type last_type = elements.get(0).accept(this);
            el.add(new ListNameType(last_type));
            for(int i=1; i<elements.size(); i++){
                Type new_type = elements.get(i).accept(this);
                el.add(new ListNameType(new_type));
                last_type = new_type;
            }
        }
        return new ListType(el);
    }

    @Override
    public Type visit(NullValue nullValue) {
        this.lvalue = true;
        return new NullType();
    }

    @Override
    public Type visit(IntValue intValue) {
        this.lvalue = true;
        return new IntType();
    }

    @Override
    public Type visit(BoolValue boolValue) {
        this.lvalue = true;
        return new BoolType();
    }

    @Override
    public Type visit(StringValue stringValue) {
        this.lvalue = true;
        return new StringType();
    }

    public boolean isSubType(Type a, Type b){
        if(a instanceof NoType){
            return true;
        }
        if(b instanceof NoType){
            return false;
        }
        if((a instanceof IntType && b instanceof IntType) || (a instanceof StringType && b instanceof StringType)
                ||(a instanceof BoolType && b instanceof BoolType) || (a instanceof NullType && b instanceof NullType)){
            return true;
        }
        if((a instanceof ClassType || a instanceof NullType) && (b instanceof ClassType || b instanceof NullType)){
            if(a instanceof NullType)
                return true;
            if(b instanceof NullType)
                return false;
            ClassType classA = (ClassType) a;
            ClassType classB = (ClassType) b;
            return classHierarchy.isSecondNodeAncestorOf(classA.getClassName().getName(), classB.getClassName().getName());
        }
        if((a instanceof FptrType || a instanceof NullType) && (b instanceof FptrType || b instanceof NullType)){
            if(a instanceof NullType)
                return true;
            if(b instanceof NullType)
                return false;
            FptrType fptrA = (FptrType) a;
            FptrType fptrB = (FptrType) b;
            if(!(isSubType(fptrA.getReturnType(), fptrB.getReturnType()))){
                return false;
            }
            else{
                ArrayList<Type> argsA = fptrA.getArgumentsTypes();
                ArrayList<Type> argsB = fptrB.getArgumentsTypes();
                if(argsA.size() != argsB.size()){
                    return false;
                }
                int size = argsA.size();
                for(int j = 0; j < size; j++){
                    if(!isSubType(argsB.get(j), argsA.get(j))){
                        return false;
                    }
                }
                return true;
            }
        }
        if(a instanceof ListType && b instanceof ListType){
            ListType lstA = (ListType) a;
            ListType lstB = (ListType) b;
            ArrayList<ListNameType> elementsTypesA = lstA.getElementsTypes();
            ArrayList<ListNameType> elementsTypesB = lstB.getElementsTypes();
            if(elementsTypesA.size() != elementsTypesB.size()){
                return false;
            }
            int size = elementsTypesA.size();
            for(int i = 0; i<size; i++){
                if(!isSubType(elementsTypesA.get(i).getType(), elementsTypesB.get(i).getType())) {
                    return false;
                }
            }
            return true;
        }
        return false;
        }
    public void Validate(Type type){
        if(type instanceof ClassType){
            ClassType ctype = (ClassType)type;
            try{
                SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + ctype.getClassName().getName(), true);
            }
            catch (ItemNotFoundException exc){
                this.err++;
                return;
            }
        }
        else if(type instanceof ListType){
            ListType ltype = (ListType) type;
            if(ltype.getElementsTypes().size() == 0){
                this.err++;
                return;
            }
            int size = ltype.getElementsTypes().size();
            ArrayList<ListNameType> arr = ltype.getElementsTypes();
            ArrayList<String>ids = new ArrayList<>();
            boolean same = false;
            for(int i = 0; i < size; i++){
                if(!arr.get(i).getName().getName().equals("")){
                    if(ids.contains(arr.get(i).getName().getName()) && !same){
                        this.err++;
                    }
                    ids.add(arr.get(i).getName().getName());
                }
                Validate(arr.get(i).getType());
            }
        }
        else if(type instanceof FptrType){
            FptrType fptrType = (FptrType)type;
            for(Type arg: fptrType.getArgumentsTypes()){
                Validate(arg);
            }
            Validate(fptrType.getReturnType());
        }
    }
}
