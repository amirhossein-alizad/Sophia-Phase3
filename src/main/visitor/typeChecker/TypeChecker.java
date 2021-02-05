package main.visitor.typeChecker;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.classDec.ClassDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.ConstructorDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.FieldDeclaration;
import main.ast.nodes.declaration.classDec.classMembersDec.MethodDeclaration;
import main.ast.nodes.declaration.variableDec.VarDeclaration;
import main.ast.nodes.expression.BinaryExpression;
import main.ast.nodes.expression.Expression;
import main.ast.nodes.expression.MethodCall;
import main.ast.nodes.expression.UnaryExpression;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.statement.*;
import main.ast.nodes.statement.loop.BreakStmt;
import main.ast.nodes.statement.loop.ContinueStmt;
import main.ast.nodes.statement.loop.ForStmt;
import main.ast.nodes.statement.loop.ForeachStmt;
import main.ast.types.NoType;
import main.ast.types.NullType;
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
import main.symbolTable.items.*;
import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;
import main.ast.types.Type;

import javax.xml.validation.Validator;
import java.util.ArrayList;

public class TypeChecker extends Visitor<Void> {
    private final Graph<String> classHierarchy;
    private final ExpressionTypeChecker expressionTypeChecker;
    private ClassDeclaration currentClassName;
    private MethodDeclaration currentMethodName;
    private Integer loops = 0;

    public TypeChecker(Graph<String> classHierarchy) {
        this.classHierarchy = classHierarchy;
        this.expressionTypeChecker = new ExpressionTypeChecker(classHierarchy);
    }

    @Override
    public Void visit(Program program) {

        boolean is_main = false;
        for(ClassDeclaration classDeclaration : program.getClasses()) {
            if(classDeclaration.getClassName().getName().equals("Main"))
                is_main = true;
            currentClassName = classDeclaration;
            expressionTypeChecker.setCurrentClassName(classDeclaration);
            classDeclaration.accept(this);

        }
        if(!is_main)
            program.addError(new NoMainClass());
        return null;
    }

    @Override
    public Void visit(ClassDeclaration classDeclaration) {
        //TODO
        String parent;
        if(classDeclaration.getParentClassName() != null) {
            parent = classDeclaration.getParentClassName().getName();
            if (parent.equals("Main")) {
                classDeclaration.addError(new CannotExtendFromMainClass(classDeclaration.getLine()));
            }
            if (classDeclaration.getClassName().getName().equals("Main")){
                classDeclaration.addError(new MainClassCantExtend(classDeclaration.getLine()));
            }
            try {
                ClassSymbolTableItem classItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY
                        + parent, true);
            }
            catch (ItemNotFoundException ignored) {
                classDeclaration.addError(new ClassNotDeclared(classDeclaration.getLine(), parent));
            }
        }
        if(classDeclaration.getClassName().getName().equals("Main") && classDeclaration.getConstructor() == null ){
            classDeclaration.addError(new NoConstructorInMainClass(classDeclaration));
        }
        for(FieldDeclaration fieldDeclaration : classDeclaration.getFields()) {
            fieldDeclaration.accept(this);
        }
        if(classDeclaration.getConstructor() != null) {
            currentMethodName = classDeclaration.getConstructor();
            expressionTypeChecker.setCurrentMethodName(currentMethodName);
            classDeclaration.getConstructor().accept(this);
            this.loops = 0;
        }
        for(MethodDeclaration methodDeclaration : classDeclaration.getMethods()) {
            currentMethodName = methodDeclaration;
            expressionTypeChecker.setCurrentMethodName(currentMethodName);
            methodDeclaration.accept(this);
            this.loops = 0;
        }
        return null;
    }

    @Override
    public Void visit(ConstructorDeclaration constructorDeclaration) {
        //TODO
        String ConstructName = constructorDeclaration.getMethodName().getName();
        if(currentClassName.getClassName().getName().equals("Main") && constructorDeclaration.getArgs().size() != 0) {
            constructorDeclaration.addError(new MainConstructorCantHaveArgs(constructorDeclaration.getLine()));
        }
        if (!currentClassName.getClassName().getName().equals(ConstructName)){
            constructorDeclaration.addError(new ConstructorNotSameNameAsClass(constructorDeclaration.getLine()));
        }
        for(VarDeclaration arg: constructorDeclaration.getArgs()){
            arg.accept(this);
        }
        for(VarDeclaration arg: constructorDeclaration.getLocalVars()){
            arg.accept(this);
        }
        for(Statement st: constructorDeclaration.getBody()){
            st.accept(this);
        }

        return null;
    }

    @Override
    public Void visit(MethodDeclaration methodDeclaration) {
        //TODO
        boolean has_return = false;
        for(VarDeclaration arg: methodDeclaration.getArgs()){
            arg.accept(this);
        }
        for(VarDeclaration arg: methodDeclaration.getLocalVars()){
            arg.accept(this);
        }
        for(Statement st: methodDeclaration.getBody()){
            if(st instanceof ReturnStmt){
                has_return = true;
            }
            st.accept(this);
        }
        if(!has_return && !(methodDeclaration.getReturnType() instanceof NullType)){
            methodDeclaration.addError(new MissingReturnStatement(methodDeclaration));
        }
        Validate(methodDeclaration, methodDeclaration.getReturnType());
        return null;
    }

    @Override
    public Void visit(FieldDeclaration fieldDeclaration) {
        //TODO

        fieldDeclaration.getVarDeclaration().accept(this);
        return null;
    }

    @Override
    public Void visit(VarDeclaration varDeclaration) {
        varDeclaration = CheckVarDec(varDeclaration, varDeclaration.getType());
        return null;
    }

    @Override
    public Void visit(AssignmentStmt assignmentStmt) {
        //TODO
        expressionTypeChecker.setLvalue(false);
        Type lhsType = assignmentStmt.getlValue().accept(expressionTypeChecker);
        boolean lval = expressionTypeChecker.getLvalue();
        Type rhsType = assignmentStmt.getrValue().accept(expressionTypeChecker);
        if (lval){
            int line = assignmentStmt.getLine();
            assignmentStmt.addError(new LeftSideNotLvalue(line));
        }
        if(lhsType instanceof NoType)
            return null;
        if (!isSubType(rhsType, lhsType)) {
            int line = assignmentStmt.getLine();
            assignmentStmt.addError(new UnsupportedOperandType(line, BinaryOperator.assign.toString()));
        }
        return null;
    }

    @Override
    public Void visit(BlockStmt blockStmt) {
        //TODO
        if (blockStmt != null) {
            for (Statement statement : blockStmt.getStatements())
                statement.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(ConditionalStmt conditionalStmt) {
        //TODO
        Type condType = conditionalStmt.getCondition().accept(expressionTypeChecker);
        if (!(condType instanceof BoolType || condType instanceof NoType)){
            conditionalStmt.addError(new ConditionNotBool(conditionalStmt.getLine()));
        }
        //inaro motmaen nistaaaaaaaaaaaaaam
        if(conditionalStmt.getThenBody() != null)
            conditionalStmt.getThenBody().accept(this);
        if(conditionalStmt.getElseBody() != null)
            conditionalStmt.getElseBody().accept(this);
        return null;
    }

    @Override
    public Void visit(MethodCallStmt methodCallStmt) {
        expressionTypeChecker.set_method_statement(true);
        Type retType = methodCallStmt.getMethodCall().accept(expressionTypeChecker);
        expressionTypeChecker.set_method_statement(false);
        return null;
    }

    @Override
    public Void visit(PrintStmt print) {
        //TODO
        if (print != null){
            Type printType = print.getArg().accept(expressionTypeChecker);
            if(!(printType instanceof IntType || printType instanceof StringType ||
                    printType instanceof BoolType || printType instanceof NoType)){
                print.addError(new UnsupportedTypeForPrint(print.getLine()));
            }
        }
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {
        //TODO
        if(returnStmt != null){
            if(returnStmt.getReturnedExpr() != null){
                Type returnType = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
                Type func_type = currentMethodName.getReturnType();
                if(!isSubType(returnType,func_type)){
                    returnStmt.addError(new ReturnValueNotMatchMethodReturnType(returnStmt));
                }
            }
        }
        return null;
    }

    @Override
    public Void visit(BreakStmt breakStmt) {
        //TODO
        if(loops <= 0) {
            breakStmt.addError(new ContinueBreakNotInLoop(breakStmt.getLine(),0));
        }
        return null;
    }

    @Override
    public Void visit(ContinueStmt continueStmt) {
        //TODO
        if(loops <= 0) {
            continueStmt.addError(new ContinueBreakNotInLoop(continueStmt.getLine(),1));
        }
        return null;
    }

    @Override
    public Void visit(ForeachStmt foreachStmt) {
        //TODO
        Type listType = null;
        loops += 1;
        if(foreachStmt.getList() != null)
            listType = foreachStmt.getList().accept(expressionTypeChecker);
        if(listType instanceof NoType){
            foreachStmt.getBody().accept(this);
            return null;
        }
        if (!(listType instanceof ListType)){
            foreachStmt.addError(new ForeachCantIterateNoneList(foreachStmt.getLine()));
            foreachStmt.getBody().accept(this);
            return null;
        }
        else{
            Type varType = foreachStmt.getVariable().accept(expressionTypeChecker);
            ListType lst = (ListType) listType;
            ArrayList<ListNameType> arr = lst.getElementsTypes();
            if(arr.size() > 0){
                Type last = arr.get(0).getType();
                int size = arr.size();
                for(int i = 1; i < size; i++){
                    Type curr = arr.get(i).getType();
                    if(!isSubType(last, curr)){
                        foreachStmt.addError(new ForeachListElementsNotSameType(foreachStmt.getLine()));
                        if(!isSubType(varType, arr.get(0).getType())){
                            foreachStmt.addError(new ForeachVarNotMatchList(foreachStmt));
                        }
                        foreachStmt.getBody().accept(this);
                        loops -= 1;
                        return null;
                    }
                }
                if(!isSubType(varType, arr.get(0).getType())){
                    foreachStmt.addError(new ForeachVarNotMatchList(foreachStmt));
                }
            }
            foreachStmt.getBody().accept(this);
        }
        loops -= 1;
        return null;
    }

    @Override
    public Void visit(ForStmt forStmt) {
        //TODO
        loops += 1;
        if(forStmt.getCondition() != null){
            Type condType = forStmt.getCondition().accept(expressionTypeChecker);
            if (!(condType instanceof BoolType || condType instanceof NoType)) {
                forStmt.addError(new ConditionNotBool(forStmt.getLine()));
            }
        }
        if(forStmt.getInitialize() != null)
            forStmt.getInitialize().accept(this);
        if(forStmt.getUpdate() != null)
            forStmt.getUpdate().accept(this);
        if(forStmt.getBody() != null)
            forStmt.getBody().accept(this);
        loops -= 1;
        return null;
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
        if((a instanceof ClassType || a instanceof NullType) && b instanceof ClassType){
            if(a instanceof NullType)
                return true;
            ClassType classA = (ClassType) a;
            ClassType classB = (ClassType) b;
            return classHierarchy.isSecondNodeAncestorOf(classA.getClassName().getName(), classB.getClassName().getName());
        }
        if((a instanceof FptrType || a instanceof NullType) && b instanceof FptrType){
            if(a instanceof NullType)
                return true;
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

    public VarDeclaration CheckVarDec(VarDeclaration varDeclaration, Type varDeclarationType){
        if(varDeclarationType instanceof ClassType){
            ClassType ctype = (ClassType)varDeclarationType;
            try{
                SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + ctype.getClassName().getName(), true);
            }
            catch (ItemNotFoundException exc){
                varDeclaration.addError(new ClassNotDeclared(varDeclaration.getLine(), ctype.getClassName().getName()));
                varDeclaration.setType(new NoType());
//                change_var_to_NoType(varDeclaration);
                return varDeclaration;
            }
        }
        else if(varDeclarationType instanceof ListType){
            ListType ltype = (ListType) varDeclarationType;
            if(ltype.getElementsTypes().size() == 0){
                varDeclaration.addError(new CannotHaveEmptyList(varDeclaration.getLine()));
                varDeclaration.setType(new NoType());
//                change_var_to_NoType(varDeclaration);
                return varDeclaration;
            }
            int size = ltype.getElementsTypes().size();
            ArrayList<ListNameType> arr = ltype.getElementsTypes();
            ArrayList<String>ids = new ArrayList<>();
            boolean same = false;
            for(int i = 0; i < size; i++){
                if(!arr.get(i).getName().getName().equals("")){
                    if(ids.contains(arr.get(i).getName().getName()) && !same){
                        varDeclaration.addError(new DuplicateListId(varDeclaration.getLine()));
                        varDeclaration.setType(new NoType());
//                        change_var_to_NoType(varDeclaration);
                        same = true;
                    }
                    ids.add(arr.get(i).getName().getName());
                }
                varDeclaration = CheckVarDec(varDeclaration, arr.get(i).getType());
            }
        }
        else if(varDeclarationType instanceof FptrType){
            FptrType fptrType = (FptrType)varDeclarationType;
            for(Type arg: fptrType.getArgumentsTypes()){
                varDeclaration = CheckVarDec(varDeclaration, arg);
            }
            varDeclaration = CheckVarDec(varDeclaration, fptrType.getReturnType());
        }
        return varDeclaration;
    }
    public void change_var_to_NoType(VarDeclaration varDeclaration){
        try {
            ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + currentClassName.getClassName().getName(), true);
            FieldSymbolTableItem fieldSymbolTableItem = (FieldSymbolTableItem)classSymbolTableItem.getClassSymbolTable().getItem(FieldSymbolTableItem.START_KEY + varDeclaration.getVarName().getName(), true);
            fieldSymbolTableItem.setType(new NoType());
        }
        catch(ItemNotFoundException exc){
            try{
                ClassSymbolTableItem classSymbolTableItem = (ClassSymbolTableItem) SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + currentClassName.getClassName().getName(), true);
                MethodSymbolTableItem methodSymbolTableItem = (MethodSymbolTableItem) classSymbolTableItem.getClassSymbolTable().getItem(MethodSymbolTableItem.START_KEY + currentMethodName.getMethodName().getName(), true);
                LocalVariableSymbolTableItem localVariableSymbolTableItem = (LocalVariableSymbolTableItem) methodSymbolTableItem.getMethodSymbolTable().getItem(LocalVariableSymbolTableItem.START_KEY + varDeclaration.getVarName(), true);
                localVariableSymbolTableItem.setType(new NoType());
            }
            catch(ItemNotFoundException ex){

            }
        }
    }
    public MethodDeclaration Validate(MethodDeclaration methodDeclaration, Type varDeclarationType){
        if(varDeclarationType instanceof ClassType){
            ClassType ctype = (ClassType)varDeclarationType;
            try{
                SymbolTable.root.getItem(ClassSymbolTableItem.START_KEY + ctype.getClassName().getName(), true);
            }
            catch (ItemNotFoundException exc){
                methodDeclaration.addError(new ClassNotDeclared(methodDeclaration.getLine(), ctype.getClassName().getName()));
//                change_var_to_NoType(varDeclaration);
                return methodDeclaration;
            }
        }
        else if(varDeclarationType instanceof ListType){
            ListType ltype = (ListType) varDeclarationType;
            if(ltype.getElementsTypes().size() == 0){
                methodDeclaration.addError(new CannotHaveEmptyList(methodDeclaration.getLine()));
//                change_var_to_NoType(varDeclaration);
                return methodDeclaration;
            }
            int size = ltype.getElementsTypes().size();
            ArrayList<ListNameType> arr = ltype.getElementsTypes();
            ArrayList<String>ids = new ArrayList<>();
            boolean same = false;
            for(int i = 0; i < size; i++){
                if(!arr.get(i).getName().getName().equals("")){
                    if(ids.contains(arr.get(i).getName().getName()) && !same){
                        methodDeclaration.addError(new DuplicateListId(methodDeclaration.getLine()));
//                        change_var_to_NoType(varDeclaration);
                        same = true;
                    }
                    ids.add(arr.get(i).getName().getName());
                }
                methodDeclaration = Validate(methodDeclaration, arr.get(i).getType());
            }
        }
        else if(varDeclarationType instanceof FptrType){
            FptrType fptrType = (FptrType)varDeclarationType;
            for(Type arg: fptrType.getArgumentsTypes()){
                methodDeclaration = Validate(methodDeclaration, arg);
            }
            methodDeclaration = Validate(methodDeclaration, fptrType.getReturnType());
        }
        return methodDeclaration;
    }

}


