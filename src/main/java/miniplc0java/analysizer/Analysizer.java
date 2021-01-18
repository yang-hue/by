package miniplc0java.analysizer;
import com.google.errorprone.annotations.Var;
import miniplc0java.tokenizer.*;
import org.checkerframework.checker.units.qual.A;

import javax.print.DocFlavor;
import java.awt.print.PrinterGraphics;
import java.io.*;
import java.util.*;

public class Analysizer {
    Tokenizer tokenizer;
    int pos;
    boolean FalseToJump = false;
    boolean isCreatingFunction = false;
    FileOutputStream stream ;
    Stack stack = new Stack();
    public FunctionList functionList = new FunctionList();
    public SymbolTable symbolTable = new SymbolTable();
    static String[] stdio = {
            "getint","getdouble","getchar","putint","putdouble","putchar","putstr","putln"};
    public Token currentToken()
    {
        return tokenizer.TokenList.get(pos);
    }
    public Token nextToken()
    {
        return pos+1<=tokenizer.TokenList.size()-1?tokenizer.TokenList.get(pos+1):null;
    }
    public Token next_next_Token()
    {
        return pos+2<=tokenizer.TokenList.size()-1?tokenizer.TokenList.get(pos+2):null;
    }
    public void pushVarToStack(Variable variable) {
        if (variable.isGlobal) {
            functionList.add_instruction("globa",Instruction.get_byte_array_by_int(variable.offset));
            stack.push(SlotType.ADDR);
        }
        else if(variable.isParam)
        {
            functionList.add_instruction("arga",Instruction.get_byte_array_by_int(variable.offset));
            stack.push(SlotType.ADDR);

        }else {
            functionList.add_instruction("loca",Instruction.get_byte_array_by_int(variable.offset));
            stack.push(SlotType.ADDR);
        }
    }
    public  ArrayList<Integer> whileStartIndexList = new ArrayList<>();
    public Token expect(TokenType tt)
    {
        if(currentToken().tokenType==tt)
        {
            Token t= currentToken();
            next();
            return t;
        }
        else throw new Error(tt.toString() + " is expected,but "+currentToken().tokenType.toString()+" is gotten");
    }
    public void next()
    {
        pos+=1;
        while (currentToken().tokenType==TokenType.COMMENT)
            pos+=1;
    }
    public Analysizer(String inputSrc,String out) throws IOException {
        tokenizer = new Tokenizer(inputSrc);
        tokenizer.TokenizerInit();
        pos = 0;
        symbolTable.addLine();
        functionList.addFunction("_start");
        stream = new FileOutputStream(out);
        analyseProgram();
        print_out();
    }
    public void analyse_expr_1()
    {// 比较表达式的值只出现在if/while语句中
        analyse_expr_2();
        while(currentToken().tokenType==TokenType.LT|| //<
                currentToken().tokenType==TokenType.GT||// >
                currentToken().tokenType==TokenType.GE||// >=
                currentToken().tokenType==TokenType.LE ||// <=
                currentToken().tokenType==TokenType.EQ||// ==
                currentToken().tokenType==TokenType.NEQ// !=
        )
        {
            TokenType compareSign = currentToken().tokenType;
            next();
            analyse_expr_2();
            if(!((stack.top()==SlotType.INT || stack.top()==SlotType.DOUBLE)&&stack.lower_top()==stack.top()))//
                throw new Error("the type on the stack can't be compared.");
                functionList.add_instruction(stack.top()==SlotType.INT? "cmp.i":"cmp.f");
                stack.pop();
                stack.pop();
                stack.push(SlotType.BOOL);
                if(compareSign==TokenType.LT) {
                    functionList.add_instruction("set.lt");
                    // 为0跳转 br.false
                    FalseToJump = true;
                }else if(compareSign==TokenType.LE) {
                    functionList.add_instruction("set.gt");
                    // 不为0跳转
                    FalseToJump = false;
                }else if(compareSign==TokenType.GT) {
                    functionList.add_instruction("set.gt");
                    // 为0跳转
                    FalseToJump = true;
                }else if(compareSign==TokenType.GE) {
                    functionList.add_instruction("set.lt");
                    // 不为0跳转
                    FalseToJump = false;
                }else if(compareSign==TokenType.EQ) {
                    // 不为0跳转
                    FalseToJump = false;
                }else if(compareSign==TokenType.NEQ) {
                    //为0 跳转
                    FalseToJump = true;
                }
        }

    }
    public void analyse_expr_2()
    {
        analyse_expr_3();
        boolean isAdding;
        Token t=currentToken();
        while(currentToken().tokenType==TokenType.PLUS|| currentToken().tokenType==TokenType.MINUS
        )
        {
            isAdding = currentToken().tokenType==TokenType.PLUS;
            next();
            analyse_expr_3();
            // 分为整数相加减 和 浮点数相加减
            if(stack.top()==stack.lower_top()&&stack.top()==SlotType.INT)
            {
                String ins = isAdding? "add.i":"sub.i";
                functionList.add_instruction(ins);
                stack.pop(SlotType.INT);
                stack.pop(SlotType.INT);
                stack.push(SlotType.INT);
            }
            else if(stack.top()==stack.lower_top()&&stack.top()==SlotType.DOUBLE)
            {
                String ins = isAdding? "add.f":"sub.f";
                functionList.add_instruction(ins);
                stack.pop(SlotType.DOUBLE);
                stack.pop(SlotType.DOUBLE);
                stack.push(SlotType.DOUBLE);
            }
        }

    }
    public void analyse_expr_3()
    {
        analyse_expr_4();
        boolean isMulting;
        Token token =currentToken();
        while(currentToken().tokenType==TokenType.MUL||
                currentToken().tokenType==TokenType.DIV
        )
        {
            isMulting = currentToken().tokenType==TokenType.MUL;
            next();
            analyse_expr_4();
            // 分为整数相乘除 和 浮点数

            if(stack.top()==stack.lower_top()&&stack.top()==SlotType.INT)
            {
                String ins = isMulting? "mul.i":"div.i";
                functionList.add_instruction(ins);
                stack.pop(SlotType.INT);
                stack.pop(SlotType.INT);
                stack.push(SlotType.INT);
            }
            else if(stack.top()==stack.lower_top()&&stack.top()==SlotType.DOUBLE)
            {
                String ins = isMulting? "mul.f":"div.f";
                functionList.add_instruction(ins);
                stack.pop(SlotType.DOUBLE);
                stack.pop(SlotType.DOUBLE);
                stack.push(SlotType.DOUBLE);
            }
        }

    }
    public void analyse_expr_4()
    {
        analyse_expr_5();
        while(currentToken().tokenType==TokenType.AS_KW
        )// 类型转换 itof,ftoi
        {
            next();
            VariableType type = analyse_type();
            // 栈顶的值不能参与类型转换
            if(stack.top()!=SlotType.DOUBLE&&stack.top()!=SlotType.INT)
                throw new Error("the type on the top of the stack can't be transfered.");
            else {
                if(stack.top()==SlotType.INT&&type==VariableType.DOUBLE)
                    functionList.add_instruction("itof");
                else   if(stack.top()==SlotType.DOUBLE&&type==VariableType.INT)
                    functionList.add_instruction("ftoi");
                stack.pop();
                stack.push(type);
                 }
        }

    }
    public VariableType analyse_type() {
        if (currentToken().tokenType != TokenType.IDENT)
            throw new Error("A type is needed(int,double,or void),but " + currentToken().tokenType.toString() + "is gotten");
        else if (currentToken().value.equals("double") )
        {
            next();
            return VariableType.DOUBLE;
        }
        else if (currentToken().value.equals("int") )
        {
            next();
            return VariableType.INT;
        }
        else if (currentToken().value.equals("void") )
        {
            next();
            return VariableType.VOID;

        }
        else {
            throw new Error("A type is needed(int,double,or void),but " + currentToken().value.toString() + " is gotten");
        }
    }
    // expr_5 -> -expr_5 | Ident(expr,expr...) |(expr)| 15| 15.6E4 | "sadfasd"
    public void analyse_expr_5()
    {
        // 先处理负号
        boolean isNegative = false;
        while(currentToken().tokenType==TokenType.MINUS)
        {
            next();
            isNegative = !isNegative;
        }

        if (currentToken().tokenType==TokenType.IDENT)
        {
            // 函数调用
            if(nextToken().tokenType==TokenType.L_PAREN)
            {
                String function_name = currentToken().value.toString();
                boolean isStdioFunc = false;
                for(int i=0;i<8;i++)
                {
                    if(function_name.equals(stdio[i]))
                    {
                        isStdioFunc = true;
                        break;
                    }
                }
                if(isStdioFunc)
                    stdio_func();
                else
                {
                    if(functionList.getFunctionByName(function_name)==null)
                        throw new Error("Function not exist.");
                    Function func = functionList.getFunctionByName(function_name);
                    if(func.return_slot!=0)
                        functionList.add_instruction("stackalloc",Instruction.get_byte_array_by_int(1));
                    int stackSize = stack.stack.size();
                    int paramIndex = 0;// 0 或 1
                    next();
                    expect(TokenType.L_PAREN);
                    if(func.param_slot!=0)
                    {
                        analyseExpr();
                        if(!(stack.stack.size()==stackSize+1&&Stack.toVT(stack.top())==func.paramVariables.get(paramIndex).variableType))
                            throw new Error("param error");
                        stackSize++;
                        paramIndex++;
                    }
                   for(int i=0;i<func.param_slot-1;i++)
                    {

                        expect(TokenType.COMMA);
                        analyseExpr();
                        if(!(stack.stack.size()==stackSize+1&&Stack.toVT(stack.top())==func.paramVariables.get(paramIndex).variableType))
                            throw new Error("param error");
                        stackSize++;
                        paramIndex++;
                    }
                   functionList.add_instruction("call",Instruction.get_byte_array_by_int(functionList.getFunctionIndex(func.name)));
                    expect(TokenType.R_PAREN);
                    for(int i=0;i<func.param_slot;i++)
                    {
                        stack.pop();
                    }
                    if (func.type!=VariableType.VOID)
                    stack.push(func.type);
                }
            }
            else  // 引用符号表的参数，查询是否存在，并把他的地址放在栈上,load
            {
                Variable variable = symbolTable.getVariableByName(currentToken().value.toString());
                pushVarToStack(variable);
                if(nextToken().tokenType!=TokenType.ASSIGN)// 不是赋值语句
                {
                    functionList.add_instruction("load.64");
                    stack.pop(SlotType.ADDR);
                    stack.push(variable.variableType);
                }else if(variable.isConst)throw new Error("const variable cannot be assigned.");
                next();
            }

        }
        // 括号
        else if(currentToken().tokenType==TokenType.L_PAREN)
        {
            expect(TokenType.L_PAREN);
            analyseExpr();
            expect(TokenType.R_PAREN);
        }
        // 字面量
        else if(currentToken().tokenType==TokenType.UINT_LITERAL)
        {
            //把数据push到栈上
            functionList.add_instruction("push",Instruction.get_byte_array_by_long((long)currentToken().value));
            stack.push(SlotType.INT);
            FalseToJump = true;
           next();
        }
        else if(currentToken().tokenType==TokenType.DOUBLE_LITERAL)
        {
            //把数据push到栈上
            functionList.add_instruction("push",Instruction.get_byte_array_by_long(Double.doubleToLongBits(
                    (double) currentToken().value
            )));
            stack.push(SlotType.DOUBLE);
            next();
        }
        else if(currentToken().tokenType==TokenType.STRING_LITERAL)
        {
            //把数据存到全局变量，把全局变量的标号（long）push到栈上
            String s = currentToken().value.toString();
            Variable variable = new Variable(s,true,true,false,VariableType.STRING);
            symbolTable.symbol_table.get(0).add(variable);
            functionList.addVariable(variable);
            functionList.add_instruction("push",Instruction.get_byte_array_by_long(
                    variable.offset
            ));
            stack.push(SlotType.INT);
            next();
        }
        else throw new Error("error occured pos = "+currentToken().startPos.toString());
        if(isNegative)
        {
            if(stack.top()==SlotType.INT)
                functionList.add_instruction("neg.i");
            else if(stack.top()==SlotType.DOUBLE)
                functionList.add_instruction("neg.f");
            else throw new Error("Address or void cannot be negated.");
        }
    }
    public void stdio_func()
    {
        String function_name = currentToken().value.toString();
        next();
        if(function_name.equals("getint"))
        {
            expect(TokenType.L_PAREN);
            expect(TokenType.R_PAREN);
            functionList.add_instruction("scan.i");
            stack.push(SlotType.INT);
        }
        else if(function_name.equals("getdouble"))
        {
            expect(TokenType.L_PAREN);
            expect(TokenType.R_PAREN);
            functionList.add_instruction("scan.f");
            stack.push(SlotType.DOUBLE);
        }
        else if(function_name.equals("getchar"))
        {
            expect(TokenType.L_PAREN);
            expect(TokenType.R_PAREN);
            functionList.add_instruction("scan.c");
            stack.push(SlotType.INT);
        }
        else if(function_name.equals("putint"))
        {
            expect(TokenType.L_PAREN);
            analyseExpr();
            expect(TokenType.R_PAREN);
            functionList.add_instruction("print.i");
            stack.pop(SlotType.INT);
        }
        else if(function_name.equals("putdouble"))
        {
            expect(TokenType.L_PAREN);
            analyseExpr();
            expect(TokenType.R_PAREN);
            functionList.add_instruction("print.f");
            stack.pop(SlotType.DOUBLE);
        }
        else if(function_name.equals("putchar"))
        {
            expect(TokenType.L_PAREN);
            analyseExpr();
            expect(TokenType.R_PAREN);
            functionList.add_instruction("print.c");
            stack.pop(SlotType.INT);
        }
        else if(function_name.equals("putstr"))
        {
            expect(TokenType.L_PAREN);
            analyseExpr();
            expect(TokenType.R_PAREN);
            functionList.add_instruction("print.s");
            stack.pop(SlotType.INT);
        }
        else if(function_name.equals("putln"))
        {
            expect(TokenType.L_PAREN);
            expect(TokenType.R_PAREN);
            functionList.add_instruction("println");
        }

    }
    public void analyseExpr() // 表达式
    {// 赋值表达式的值是void 不能被使用
        analyse_expr_1();
       if(currentToken().tokenType==TokenType.ASSIGN)
        {
            next();
            analyse_expr_1();
            if((stack.top()==SlotType.INT||stack.top()==SlotType.DOUBLE)&&stack.lower_top()==SlotType.ADDR)
            {
                functionList.add_instruction("store.64");
                stack.pop();
                stack.pop(SlotType.ADDR);
            }else throw new Error("");
        }
    }

// 可以在开始定义全局变量，也可以在函数内定义局部变量
    public void analyse_const_decl_stmt() // 常量赋值语句
    {
        expect(TokenType.CONST_KW);
        Token token=expect(TokenType.IDENT);
        expect(TokenType.COLON);
        VariableType type = analyse_type();
        if(type==VariableType.VOID)
            throw new Error("const item can't be defined as void,pos: "+currentToken().startPos.toString());
        boolean global = symbolTable.symbol_table.size()==1;
        Variable v = new Variable(token.value.toString(),true,global,false,type);
        symbolTable.addOneVariable(v);
        functionList.addVariable(v);
        if(!global)
            functionList.top().local_slot++;
        pushVarToStack(variable);
        expect(TokenType.ASSIGN);
        analyseExpr();
        // 汇编和栈操作应该是同步的
        functionList.add_instruction("store.64");
        stack.pop(v.variableType);
        stack.pop(SlotType.ADDR);
        expect(TokenType.SEMICOLON);
    }
    public void analyse_let_decl_stmt() // 变量赋值语句
    {
        expect(TokenType.LET_KW);
        Token token = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        VariableType type = analyse_type();
        if(type==VariableType.VOID)
            throw new Error("const item can't be defined as void,pos: "+currentToken().startPos.toString());
        boolean global = symbolTable.symbol_table.size()==1;
        Variable v = new Variable(token.value.toString(),false,global,false,type);
        symbolTable.addOneVariable(v);
        functionList.addVariable(v);
        if(!global)
            functionList.top().local_slot++;
        if(currentToken().tokenType==TokenType.ASSIGN)
        {
            pushVarToStack(v);
            next();
            analyseExpr();
            // 汇编和栈操作应该是同步的
            functionList.add_instruction("store.64");
            stack.pop(v.variableType);
            stack.pop(SlotType.ADDR);
        }
        expect(TokenType.SEMICOLON);
    }
    // program -> decl_stmt* function*
    public void analyseProgram() // 整个程序
    {
        while(currentToken().tokenType==TokenType.CONST_KW || currentToken().tokenType==TokenType.LET_KW)
        {
            if(currentToken().tokenType==TokenType.CONST_KW)
            {
                analyse_const_decl_stmt();
            }
            else analyse_let_decl_stmt();
        }
        while(currentToken().tokenType==TokenType.FN_KW)
        {
            analyseFunction();
        }
        expect(TokenType.EOF);
        functionList.function_list.get(0).instructions.add(new Instruction("call",
                Instruction.get_byte_array_by_int(functionList.function_list.size()-1)));
        symbolTable.symbol_table.get(0).add(new Variable("_start",
                true,true,false,VariableType.STRING));
        functionList.function_list.get(0).id = symbolTable.GlobalVariables().size()-1;


    }
    public void analyse_function_param()
    {
        boolean isConst = false;
        if(currentToken().tokenType==TokenType.CONST_KW)
        {
            isConst = true;
            next();
        }
        Token token = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        VariableType type = analyse_type();
        if(type == VariableType.VOID)
            throw new Error("function params can't be void");
        Variable v =new Variable(token.value.toString(),isConst,false,true,type);
        symbolTable.addOneVariable(v);
        functionList.addVariable(v);
        functionList.top().param_slot+=1;
    }
    public void analyseFunction()// 函数
    {
        expect(TokenType.FN_KW);
        Token token = expect(TokenType.IDENT);

        // 要判断函数名是否和全局变量同名，以及是否有重名函数
        ArrayList<Variable> global_variables = symbolTable.GlobalVariables();
        for(int i=0;i<global_variables.size();i++)
        {
            if(token.value.toString().equals(global_variables.get(i).name))
                throw new Error("Function name cannot be the same as global variable.");
        }
        // 在函数列表里添加函数
        functionList.addFunction(token.value.toString());
        // 在全局变量里添加函数名 String
        Variable variable = new Variable(token.value.toString(),true,true,false,
                VariableType.STRING);
        symbolTable.symbol_table.get(0).add(variable);
        functionList.addVariable(variable);
        // 函数的全局变量序号
        functionList.top().id = symbolTable.GlobalVariables().size()-1;
        expect(TokenType.L_PAREN);
        symbolTable.addLine();
       if(currentToken().tokenType!=TokenType.R_PAREN)
        {
            analyse_function_param();
        }
        while(currentToken().tokenType!=TokenType.R_PAREN)
        {
            expect(TokenType.COMMA);
            analyse_function_param();
        }
        expect(TokenType.R_PAREN);
        expect(TokenType.ARROW);
        VariableType type = analyse_type();
        functionList.set_return_type(type);
        if(type == VariableType.DOUBLE || type ==VariableType.INT)
        {
            functionList.set_return_slot();
            functionList.addParamsIndex();
        }

        isCreatingFunction = true;
        analyse_block_stmt();
        // void 函数最后一句不是return
        if(functionList.top().type==VariableType.VOID&&(functionList.top().instructions.size()==0||!functionList.top().topInstruction().instruction_name.equals("ret")))
        {
            functionList.add_instruction("ret");
        }
        if(functionList.top().name.equals("main")&&functionList.top().type!=VariableType.VOID)
        {
            functionList.add_instruction("stackalloc",Instruction.get_byte_array_by_int(1));
        }
        //不是void函数返回值检查
        if(functionList.top().type!=VariableType.VOID)
        ReturnCheck();
    }
    public void analyse_stmt()//语句
    {
        if (currentToken().tokenType == TokenType.IDENT ||
                currentToken().tokenType == TokenType.L_PAREN ||
                currentToken().tokenType == TokenType.UINT_LITERAL ||
                currentToken().tokenType == TokenType.DOUBLE_LITERAL ||
                currentToken().tokenType == TokenType.STRING_LITERAL ||
                currentToken().tokenType == TokenType.MINUS)
            {// a;     a = 123;
                analyseExpr();
                expect(TokenType.SEMICOLON);
            }
        else if(currentToken().tokenType==TokenType.IF_KW)
        {
            analyse_if_stmt();
        }
        else if(currentToken().tokenType==TokenType.CONST_KW)
        {
            analyse_const_decl_stmt();
        }
        else if(currentToken().tokenType==TokenType.LET_KW)
        {
            analyse_let_decl_stmt();
        }
        else if(currentToken().tokenType==TokenType.WHILE_KW)
        {
            analyse_while_stmt();
        }
        else if(currentToken().tokenType==TokenType.RETURN_KW)
        {
            analyse_return_stmt();
        }
        else if(currentToken().tokenType==TokenType.L_BRACE)
        {
            analyse_block_stmt();
        }
        else if(currentToken().tokenType==TokenType.SEMICOLON)
        {
            next();
        }
        else if(currentToken().tokenType==TokenType.BREAK_KW)
        {
            if(whileStartIndexList.size()==0)
                throw new Error("it's not in a loop now!");
            next();
            expect(TokenType.SEMICOLON);
            functionList.add_instruction("break");
        }
        else if(currentToken().tokenType==TokenType.CONTINUE_KW)
        {
            if(whileStartIndexList.size()==0)
                throw new Error("it's not in a loop now!");
            next();
            expect(TokenType.SEMICOLON);
            int det = whileStartIndexList.get(whileStartIndexList.size()-1)-functionList.top().instructions.size()-1;
            functionList.add_instruction("br",Instruction.get_byte_array_by_int(det));
        }

        else throw new Error("analyse_stmt failed ,pos: "+currentToken().startPos.toString());
    }
    public int analyse_if_stmt()// 返回if语句结束时instruction的语句数
    {
        expect(TokenType.IF_KW);
        analyseExpr();
        int start_index =  functionList.top().instructions.size();
        functionList.add_instruction(FalseToJump?"br.false":"br.true");
        Instruction afterExpr = functionList.top().instructions.get(functionList.top().instructions.size()-1);
        analyse_block_stmt();
        int mid_index = functionList.top().instructions.size();
        functionList.add_instruction("br");
        Instruction afterBlock = functionList.top().instructions.get(functionList.top().instructions.size()-1);
        if(currentToken().tokenType!=TokenType.ELSE_KW)
        {
            functionList.top().instructions.remove(afterBlock);
            afterExpr.with_operands = true;
            afterExpr.instruction_num = Instruction.get_byte_array_by_int(mid_index-start_index-1);
            return functionList.top().instructions.size();
        }else {
            next();
            if(currentToken().tokenType==TokenType.IF_KW)
            {
                int res = analyse_if_stmt()-1;
                afterExpr.with_operands = true;
                afterExpr.instruction_num = Instruction.get_byte_array_by_int(mid_index-start_index);
                afterBlock.with_operands = true;
                afterBlock.instruction_num = Instruction.get_byte_array_by_int(res-mid_index);
                return res;
            }
            else {
                analyse_block_stmt();
                int end_index = functionList.top().instructions.size()-1;
                afterExpr.with_operands = true;
                afterExpr.instruction_num = Instruction.get_byte_array_by_int(mid_index-start_index);
                afterBlock.with_operands = true;
                afterBlock.instruction_num = Instruction.get_byte_array_by_int(end_index-mid_index);
                return functionList.top().instructions.size();
            }
        }
    }
    public void analyse_while_stmt()
    {
        expect(TokenType.WHILE_KW);
        int start_index = functionList.top().instructions.size();
        whileStartIndexList.add(start_index);//记录while开始的地方 continue的时候直接跳到这里
        analyseExpr();
        int origin_index = functionList.top().instructions.size();
        functionList.add_instruction(FalseToJump?"br.false":"br.true");
        analyse_block_stmt();
        int end_index  = functionList.top().instructions.size();//循环里的所有break跳到这里
        for(int i=start_index;i<end_index;i++)
        {
            Instruction instruction= functionList.top().instructions.get(i);
            if(instruction.instruction_name.equals("break"))
            {
                instruction.with_operands=true;
                instruction.instruction_num = Instruction.get_byte_array_by_int(end_index-i);
                instruction.instruction_name="br";
            }
        }
        whileStartIndexList.remove(whileStartIndexList.size()-1);
        functionList.add_instruction("br");
        functionList.top().instructions.get(origin_index).with_operands = true;
        functionList.top().instructions.get(origin_index).instruction_num = Instruction.get_byte_array_by_int(end_index-origin_index);
        functionList.top().instructions.get(end_index).with_operands = true;
        functionList.top().instructions.get(end_index).instruction_num = Instruction.get_byte_array_by_int(start_index-end_index-1 );
    }
    public void analyse_return_stmt()
    {
        expect(TokenType.RETURN_KW);
        if(currentToken().tokenType!=TokenType.SEMICOLON)
        {
            if(functionList.top().return_slot==0)
                throw new Error("this function should not have return value");
            functionList.add_instruction("arga",Instruction.get_byte_array_by_int(0));
            stack.push(SlotType.ADDR);

            analyseExpr();

            functionList.add_instruction("store.64");
            stack.pop(functionList.top().type);
            stack.pop(SlotType.ADDR);
        }else// return ;
            if(functionList.top().return_slot!=0)
            throw new Error("this function should have return value");

        functionList.add_instruction("ret");
        expect(TokenType.SEMICOLON);
        functionList.top().return_point+=1;
    }
    public void analyse_block_stmt()
    {
        expect(TokenType.L_BRACE);
        if(!isCreatingFunction)
        symbolTable.addLine();
        isCreatingFunction = false;
        while (currentToken().tokenType!=TokenType.R_BRACE)
        {
            analyse_stmt();
        }
        expect(TokenType.R_BRACE);
        symbolTable.popLine();

    }
    public void print_out() throws IOException {
        stream.write(new byte[]{0x72,0x30,0x3B,0x3E,0x00,0x00,0x00,0x01});
        //全局变量数
        int global_num=symbolTable.GlobalVariables().size();
        ArrayList<Byte> bytes = Instruction.get_byte_array_by_int(global_num);
        stream.write(toByteArray(bytes));

        // 全局变量
        for(int i=0;i<symbolTable.GlobalVariables().size();i++)
        {
            Variable variable = symbolTable.GlobalVariables().get(i);
            if(variable.variableType==VariableType.STRING)
            {
                int isConst = symbolTable.GlobalVariables().get(i).isConst? 1:0;
                stream.write(isConst);
                stream.write(toByteArray(Instruction.get_byte_array_by_int(variable.name.length())));
                for(int p=0;p<variable.name.length();p++)
                    stream.write((int)variable.name.charAt(p));
            }
            else
            {
                int isConst = symbolTable.GlobalVariables().get(i).isConst? 1:0;
                stream.write(isConst);
                stream.write(toByteArray(Instruction.get_byte_array_by_int(8)));
                stream.write(toByteArray(Instruction.get_byte_array_by_long(0)));
            }
        }
        stream.write(toByteArray(Instruction.get_byte_array_by_int(functionList.function_list.size())));
        for(int i=0;i<functionList.function_list.size();i++)
        {
            Function f= functionList.function_list.get(i);
            stream.write(toByteArray(Instruction.get_byte_array_by_int(f.id)));
            stream.write(toByteArray(Instruction.get_byte_array_by_int(f.return_slot)));
            stream.write(toByteArray(Instruction.get_byte_array_by_int(f.param_slot)));
            stream.write(toByteArray(Instruction.get_byte_array_by_int(f.local_slot)));
            stream.write(toByteArray(Instruction.get_byte_array_by_int(f.instructions.size())));
            for(int j=0;j<f.instructions.size();j++)
            {
                Instruction instruction = f.instructions.get(j);
                stream.write(instruction.instruction_byte);
                if(instruction.with_operands)
                    stream.write(toByteArray(instruction.instruction_num));
            }

        }
    }
    public static byte[] toByteArray(ArrayList<Byte> bytes)
    {
        int len = bytes.size();
        byte[] res = new byte[len];
        for(int i=0;i<len;i++)
        {
            res[i]=bytes.get(i);
        }
        return res;
    }

    public static void main(String[] args) {
        ArrayList<Byte> b = Instruction.get_byte_array_by_int(-19);
    }
    public void ReturnCheck()
    {
        int size = functionList.top().instructions.size();
        ArrayList<Instruction> insList = functionList.top().instructions;
        ArrayList<Integer> trans = new ArrayList<>();
        ArrayList<Integer> destinations = new ArrayList<>();
        ArrayList<Integer> inners = new ArrayList<>();
        inners.add(0);
        for (int i=0;i<size;i++)
        {
            Instruction instruction = insList.get(i);
            if(instruction.instruction_name.contains("br"))
            {
                trans.add(i);
                int det = (int)Instruction.get_num_by_byte_array(instruction.instruction_num);
                int destination = i+1+det;
                destinations.add(destination);
            }
        }
        for(int i=0;i<trans.size();i++)
        {
            if(!inners.contains(trans.get(i)+1))
            inners.add(trans.get(i)+1);//跳转的下一句
        }
        for(int i=0;i<destinations.size();i++)
        {
            if(!inners.contains(destinations.get(i)))
            inners.add(destinations.get(i));//直接跳转的入口
        }
        Collections.sort(inners);
        if(inners.size()==1&&functionList.top().return_point==0)
            throw new Error("error!");
        ArrayList<BasicBlock> BasicBlockList = new ArrayList<>();
        for(int i=0;i<inners.size();i++)
        {
            BasicBlock basicBlock = new BasicBlock();
            int dest = i!=inners.size()-1? inners.get(i+1):size;
            for(int j=inners.get(i);j<dest;j++)
            {
                basicBlock.phaseIndexs.add(j);
                if(functionList.top().instructions.get(j).instruction_name.equals("ret"))
                    basicBlock.hasReturn = true;
            }
            if(i!=inners.size()-1)
                    {
                basicBlock.jumpTo.add(i+1);
                if(functionList.top().instructions.get(dest-1).instruction_name.contains("br"))
                {
                    int to = (int)(dest+Instruction.get_num_by_byte_array(functionList.top().instructions.get(dest-1).instruction_num));
                    to = inners.indexOf(to);
                    basicBlock.jumpTo.add(to);
                }
                    }
            BasicBlockList.add(basicBlock);
        }
        ArrayList<Integer> signed = new ArrayList<>();
        BasicBlockReturnCheck(BasicBlockList,0,signed);
    }
    public void BasicBlockReturnCheck(ArrayList<BasicBlock> basicBlockList,int startIndex,ArrayList<Integer> signed)
    {
        BasicBlock bb = basicBlockList.get(startIndex);
        if(bb.hasReturn) return;
        //没有return
        if(startIndex==basicBlockList.size()-1)
           throw new Error("ReturnCheck failed");
        //没到最后
        for(int i=0;i<bb.jumpTo.size();i++)
        {
            if(signed.contains(bb.jumpTo.get(i)))//环
                return;
            else {
                signed.add(startIndex);
                BasicBlockReturnCheck(basicBlockList,bb.jumpTo.get(i),signed);
            }
        }
    }

}
