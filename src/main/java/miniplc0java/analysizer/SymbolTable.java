package miniplc0java.analysizer;

import java.util.ArrayList;

public class SymbolTable {
    public ArrayList<ArrayList<Variable>> symbol_table= new ArrayList<>();
    public void addLine()
    {
        ArrayList<Variable> new_variableLine = new ArrayList<>();
        symbol_table.add(new_variableLine);
    }
    public void popLine()
    {
        symbol_table.remove(symbol_table.size()-1);
    }
    public ArrayList<Variable> Top()
    {
        return symbol_table.get(symbol_table.size()-1);
    }
    public void addOneVariable(Variable variable)
    {

        for(int i=0;i<Top().size();i++)
        {
            if(Top().get(i).name.equals(variable.name))
                throw new Error("variable name redefined.");
        }
        Top().add(variable);
    }
    public Variable getVariableByName(String name)
    {
        for(int i=symbol_table.size()-1;i>=0;i--)
        {
            for(int j=symbol_table.get(i).size()-1;j>=0;j--)
            {
                if(symbol_table.get(i).get(j).name.equals(name))
                    return symbol_table.get(i).get(j);
            }
        }
        throw new Error("variable not defined.");
    }
    public ArrayList<Variable> GlobalVariables()
    {
        return symbol_table.get(0);
    }
}
