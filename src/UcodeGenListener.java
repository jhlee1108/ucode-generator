import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

import org.antlr.v4.runtime.tree.ParseTreeProperty;


public class UcodeGenListener extends MiniCBaseListener{
	private ParseTreeProperty<String> newTexts = new ParseTreeProperty<String>();
	private HashMap<String, Object> globalVar = new HashMap<String, Object>();
	private ArrayList<HashMap<String , Object>> localVarList = new ArrayList<HashMap<String,Object>>();
	private String space = "           ";
	private int offset = 1;
	private int labelNumber = 0;
	private boolean haveReturnStmt;
	private static int depth = 1;
	
	@Override
	public void enterProgram(MiniCParser.ProgramContext ctx) {
		globalVar.put("globalSize", 0);	
		globalVar.put("initGlobalVar", "");
		super.enterProgram(ctx);
	}

	@Override
	public void exitProgram(MiniCParser.ProgramContext ctx) {
		int globalSize = (int)globalVar.get("globalSize");
		String initGlobalVar = (String)globalVar.get("initGlobalVar");
		FileWriter f;
		
		try {
			f = new FileWriter("result.txt");
			for(MiniCParser.DeclContext d : ctx.decl()) {
				f.write(newTexts.get(d) + "\n");
				System.out.println(newTexts.get(d));
			}
			
			f.write(space + "bgn " + globalSize + "\n" + initGlobalVar + space + "ldp" + "\n" + space + "call main\n" +space + "end");
			System.out.println(space + "bgn " + globalSize + "\n" + initGlobalVar + space + "ldp" + "\n" + space + "call main\n" +space + "end");
			
			f.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println(globalVar.get("main"));
	}

	@Override
	public void exitDecl(MiniCParser.DeclContext ctx) {
		String decl;
		
		if(ctx.var_decl() == null) // fun_decl
			decl = newTexts.get(ctx.fun_decl());
		else // var_decl
			decl = newTexts.get(ctx.var_decl());
		
		newTexts.put(ctx, decl);
	}

	@Override
	public void exitVar_decl(MiniCParser.Var_declContext ctx) {
		String ident = ctx.getChild(1).getText();
		String varDecl;
		int base = 1;
		
		globalVar.put(ident, base + " " + offset);
		
		if(ctx.getChildCount() == 3) {	// type_spec IDENT ';'
			varDecl = space + "sym " + base + " " + offset + " " + 1;
			offset++;
		}

		else if(ctx.getChildCount() == 5) {	// type_spec IDENT '=' LITERAL ';'
			String literal = ctx.LITERAL().getText();
			varDecl = space + "sym " + base + " " + offset + " " + 1;
			globalVar.put("initGlobalVar",globalVar.get("initGlobalVar") + space + "ldc " + literal + "\n" + space + "str " + base + " " + offset + "\n");
			offset++;
		}
		
		else {	// type_spec IDENT '[' LITERAL ']' ';'
			String literal = ctx.LITERAL().getText();
			varDecl = space + "sym " + base + " " + offset + " " + literal;
			offset += Integer.parseInt(literal);
			globalVar.put(ident + "isList", true);
		}
		
		globalVar.put("globalSize", offset - 1);
		newTexts.put(ctx, varDecl);
	}

	@Override
	public void exitType_spec(MiniCParser.Type_specContext ctx) {
		newTexts.put(ctx, ctx.getChild(0).getText()); // VOID | INT
	}

	@Override
	public void enterFun_decl(MiniCParser.Fun_declContext ctx) {
		offset = 1;
		localVarList.add(new HashMap<String, Object>());
		localVarList.get(localVarList.size() - 1).put("localSize", 0);
		haveReturnStmt = false;
		globalVar.put("funcIdent", ctx.IDENT().getText());
		globalVar.put(ctx.IDENT().getText(), new Node(ctx.IDENT().getText()));
		super.enterFun_decl(ctx);
	}

	@Override
	public void exitFun_decl(MiniCParser.Fun_declContext ctx) {		
		String ident = ctx.getChild(1).getText();
		String params = newTexts.get(ctx.params());
		String compound_stmt = newTexts.get(ctx.compound_stmt());
		int localSize = (int)localVarList.get(localVarList.size() - 1).get("localSize");
		int blockNumber = 2;
		int lexicalLevel = 2;
		String funDecl;
		String proc =  ident + space.substring(ident.length(), space.length()) + "proc " + localSize + " " + blockNumber + " " + lexicalLevel;
		
		if(params.equals("") || params.equals("void")) // params == VOID | ""
			funDecl = proc + "\n" + compound_stmt;
		else // params == param (',' param)*
			funDecl = proc + "\n" + params + "\n" + compound_stmt;
		
		if(!haveReturnStmt)
			funDecl = funDecl + "\n" + space + "ret" + "\n" + space + "end";
		else
			funDecl = funDecl + "\n" + space + "end";
		
		localVarList.remove(localVarList.size() - 1);
		newTexts.put(ctx, funDecl);
	}

	@Override
	public void exitParams(MiniCParser.ParamsContext ctx) {
		String params;
		
		if(ctx.getChildCount() == 0)
			params = "";
		else if(ctx.getChild(0) != ctx.param(0)) // VOID
			params = ctx.getChild(0).getText();
		else { // param (',' param)*
			StringBuffer buf = new StringBuffer();
			
			for(MiniCParser.ParamContext p : ctx.param())
				buf.append(newTexts.get(p) + "\n");
			
			params = buf + "";
			params = params.substring(0, params.length() - 1);	// remove "\n"
		}
		
		newTexts.put(ctx, params);
	}

	@Override
	public void exitParam(MiniCParser.ParamContext ctx) {
		int base = 2;
		HashMap<String, Object> localVar = localVarList.get(localVarList.size() - 1);		
		
		localVar.put(ctx.IDENT().getText(), base + " " + offset++); // type_spec IDENT
		
		if(ctx.getChildCount() == 4) // type_spec IDENT '[' ']'
			localVar.put(ctx.IDENT().getText() + "isParamList", true);
			
		localVar.put("localSize", (int)localVar.get("localSize") + 1);
		newTexts.put(ctx, space + "sym " + localVar.get(ctx.IDENT().getText()) + " 1");
	}

	@Override
	public void exitStmt(MiniCParser.StmtContext ctx) {
		newTexts.put(ctx, newTexts.get(ctx.getChild(0))); //expr_stmt | compound_stmt | if_stmt | while_stmt | return_stmt
	}

	@Override
	public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
		newTexts.put(ctx, newTexts.get(ctx.expr())); // expr ';'
	}

	@Override
	public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
		String expr = newTexts.get(ctx.expr());
		String stmt = newTexts.get(ctx.stmt());
		String labelLoop = "$$" + labelNumber++;
		String labelEnd = "$$" + labelNumber++;
		String whileStart = labelLoop + space.substring(labelLoop.length(), space.length()) + "nop";
		String whileEnd = labelEnd + space.substring(labelEnd.length(), space.length()) + "nop";
		
		newTexts.put(ctx, whileStart + "\n" + expr + "\n" + space + "fjp " + labelEnd + "\n" + stmt + "\n" + space + "ujp " + labelLoop + "\n" + whileEnd);
	}

	@Override
	public void exitCompound_stmt(MiniCParser.Compound_stmtContext ctx) {
		StringBuffer localDeclAndStmt = new StringBuffer();
		
		for(MiniCParser.Local_declContext l : ctx.local_decl()) // local_decl*
			localDeclAndStmt.append(newTexts.get(l) + "\n");
		
		for(MiniCParser.StmtContext s : ctx.stmt()) // stmt*
			localDeclAndStmt.append(newTexts.get(s) + "\n");
		
		newTexts.put(ctx, localDeclAndStmt.substring(0, localDeclAndStmt.length() - 1)); // remove "\n"
	}

	@Override
	public void exitLocal_decl(MiniCParser.Local_declContext ctx) {
		String ident = ctx.getChild(1).getText();
		String localDecl;
		int base = 2;
		int size;
		HashMap<String, Object> localVar = localVarList.get(localVarList.size() - 1);
		
		localVar.put(ident, base + " " + offset);
		
		if(ctx.getChildCount() == 3) {	// type_spec IDENT ';'
			localDecl = space + "sym " + base + " " + offset + " " + 1;
			size = 1;
		}

		else if(ctx.getChildCount() == 5) {	// type_spec IDENT '=' LITERAL ';'
			String literal = ctx.LITERAL().getText();
			localDecl = space + "sym " + base + " " + offset + " " + 1 + "\n" 
						+ space + "ldc " + literal + "\n" + space + "str " + base + " " + offset;
			size = 1;
		}
		
		else {	// type_spec IDENT '[' LITERAL ']' ';'
			String literal = ctx.LITERAL().getText();
			localDecl = space + "sym " + base + " " + offset + " " + literal;
			size = Integer.parseInt(literal);
			localVar.put(ident + "isList", true);
		}
		
		offset += size;
		localVar.put("localSize", (int)localVar.get("localSize") + size);
		newTexts.put(ctx, localDecl);
	}

	@Override
	public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {		
		String expr = newTexts.get(ctx.expr());
		String stmt1 = newTexts.get(ctx.stmt(0));
		String ifStmt;
		String labelElse = "$$" + labelNumber++;
		
		if(ctx.getChildCount() == 5) // IF '(' expr ')' stmt
			ifStmt = expr + "\n" + space + "fjp " + labelElse + "\n" + stmt1 + "\n"
					+ labelElse + space.substring(labelElse.length(), space.length()) + "nop";
		else {	// IF '(' expr ')' stmt ELSE stmt
			String stmt2 = newTexts.get(ctx.stmt(1));
			String labelEnd = "$$" + labelNumber++;
			ifStmt = expr + "\n" + space + "fjp " + labelElse + "\n" + stmt1 + "\n" + space + "ujp " + labelEnd // IF '(' expr ')' stmt
					+ "\n" + labelElse + space.substring(labelElse.length(), space.length()) + "nop" + "\n" + stmt2 + "\n" 
					+ labelEnd + space.substring(labelEnd.length(), space.length()) + "nop";
		}
		
		newTexts.put(ctx, ifStmt);
	}

	@Override
	public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
		String returnStmt;
		
		if(ctx.expr() != null) {	// RETURN expr ';'
			String expr = newTexts.get(ctx.expr());
			returnStmt = expr + "\n" + space + "retv";
		}
		
		else	// RETURN ';'
			returnStmt = space + "ret";
		
		haveReturnStmt = true;
		newTexts.put(ctx, returnStmt);
	}

	@Override
	public void exitExpr(MiniCParser.ExprContext ctx) {
		String expr1, expr2, op, ident;
		HashMap<String, Object> localVar = localVarList.get(localVarList.size() - 1);
		
		if(ctx.getChildCount() == 1) { // LITERAL | IDENT
			String ucodeInstruction;
			
			if(ctx.LITERAL() != null) {// LITERAL
				ident = ctx.getChild(0).getText();
				ucodeInstruction = "ldc ";
			}
			
			else { // IDENT
				if(localVar.containsKey(ctx.getChild(0).getText())) {
					ident = (String)localVar.get(ctx.getChild(0).getText());
					if(localVar.containsKey(ctx.getChild(0).getText() + "isList"))
						ucodeInstruction = "lda ";						
					else
						ucodeInstruction = "lod ";
				}
				
				else {
					ident = (String)globalVar.get(ctx.getChild(0).getText());
					if(globalVar.containsKey(ctx.getChild(0).getText() + "isList"))
						ucodeInstruction = "lda ";						
					else
						ucodeInstruction = "lod ";
				}
			}
			
			newTexts.put(ctx, space + ucodeInstruction + ident);
		}
		
		else if(ctx.getChildCount() == 2){
			op = ctx.getChild(0).getText();
			expr1 = newTexts.get(ctx.expr(0));
			
			if(localVar.containsKey(ctx.expr(0).getText()))
				ident = (String)localVar.get(ctx.expr(0).getText());
			else
				ident = (String)globalVar.get(ctx.expr(0).getText());
			
			if(op.equals("-")) // '-' expr
				newTexts.put(ctx, expr1 + "\n" + space + "neg");
			
			else if(op.equals("--")) { // '--' expr
				if(ctx.parent.getChild(ctx.parent.getChildCount() - 2).getText().equals("="))
					newTexts.put(ctx, expr1 + "\n" + space + "dec" + "\n" + space + "str " + ident + "\n" + space + "lod " + ident);
				else
					newTexts.put(ctx, expr1 + "\n" + space + "dec" + "\n" + space + "str " + ident);
			}
			
			else if(op.equals("++")) { // '++' expr
				if(ctx.parent.getChild(ctx.parent.getChildCount() - 2).getText().equals("="))
					newTexts.put(ctx, expr1 + "\n" + space + "inc" + "\n" + space + "str " + ident + "\n" + space + "lod " + ident);
				else
					newTexts.put(ctx, expr1 + "\n" + space + "inc" + "\n" + space + "str " + ident);
			}
			
			else if(op.equals("!")) // '!' expr
				newTexts.put(ctx, expr1 + "\n" + space + "notop");
			
			else // '+' expr
				newTexts.put(ctx, expr1);
		}
		
		else if(ctx.getChildCount() == 3){
			if (ctx.getChild(1) != ctx.expr(0)) {
				op = ctx.getChild(1).getText();
				
				if(op.equals("=")) { // IDENT '=' expr
					if(localVar.containsKey(ctx.IDENT().getText()))
						ident = (String)localVar.get(ctx.getChild(0).getText());
					else
						ident = (String)globalVar.get(ctx.getChild(0).getText());
					
					expr1 = newTexts.get(ctx.expr(0));
					newTexts.put(ctx, expr1 + "\n" + space + "str " + ident);
				}
				
				else {
					expr1 = newTexts.get(ctx.expr(0));
					expr2 = newTexts.get(ctx.expr(1));
					String ucodeInstruction;
					
					if(op.equals("*")) // expr '*' expr
						ucodeInstruction = space + "mult";
					else if(op.equals("/")) // expr '/' expr
						ucodeInstruction = space + "div";
					else if(op.equals("%")) // expr '%' expr
						ucodeInstruction = space + "mod";
					else if(op.equals("+")) // expr '+' expr
						ucodeInstruction = space + "add";
					else if(op.equals("-")) // expr '-' expr
						ucodeInstruction = space + "sub";
					else if(op.equals("==")) // expr EQ expr
						ucodeInstruction = space + "eq";
					else if(op.equals("!=")) // expr NE expr
						ucodeInstruction = space + "ne";
					else if(op.equals("<=")) // expr LE expr
						ucodeInstruction = space + "le";
					else if(op.equals("<")) // expr '<' expr
						ucodeInstruction = space + "lt";
					else if(op.equals(">=")) // expr GE expr
						ucodeInstruction = space + "ge";
					else if(op.equals(">")) // expr '>' expr
						ucodeInstruction = space + "gt";
					else if(op.equals("and")) // expr AND expr
						ucodeInstruction = space + "and";
					else // expr OR expr
						ucodeInstruction = space + "or";
					
					newTexts.put(ctx, expr1 + "\n" + expr2 + "\n" + ucodeInstruction);
				}
			}
			
			else	// '(' expr ')'
				newTexts.put(ctx, newTexts.get(ctx.expr(0)));
		}
		
		else if(ctx.getChildCount() == 4) {
			if(ctx.expr(0) != null) {	// IDENT '[' expr ']'
				String ucodeInstruction;
				expr1 = newTexts.get(ctx.expr(0));
				
				if(localVar.containsKey(ctx.IDENT().getText())) {
					ident = (String)localVar.get(ctx.getChild(0).getText());
					
					if(localVar.containsKey(ctx.getChild(0).getText() + "isParamList"))
						ucodeInstruction = "lod ";						
					else
						ucodeInstruction = "lda ";
				}
				
				else {
					ident = (String)globalVar.get(ctx.getChild(0).getText());
					
					if(globalVar.containsKey(ctx.getChild(0).getText() + "isParamList"))
						ucodeInstruction = "lod ";						
					else
						ucodeInstruction = "lda ";
				}
				newTexts.put(ctx, expr1 + "\n" + space + ucodeInstruction + ident + "\n" + space + "add" + "\n" + space + "ldi");
			}
			
			else {	// IDENT '(' args ')'
				ident = ctx.IDENT().getText();
				expr1 = newTexts.get(ctx.args());
				newTexts.put(ctx, space + "ldp" + "\n" + expr1 + "\n" + space + "call " + ident);			
				
				Node parentNode = (Node)globalVar.get(globalVar.get("funcIdent"));
				if(globalVar.containsKey(ident) && !globalVar.get("funcIdent").equals(ident))
					parentNode.add((Node)globalVar.get(ident));
				else
					parentNode.add(new Node(ident));
				globalVar.put((String)globalVar.get("funcIdent"), parentNode);
			}
		
		}
		
		else {	// IDENT '[' expr ']' '=' expr
			String ucodeInstruction;
			expr1 = newTexts.get(ctx.expr(0));
			expr2 = newTexts.get(ctx.expr(1));
			
			if(localVar.containsKey(ctx.IDENT().getText())) {
				ident = (String)localVar.get(ctx.getChild(0).getText());
				
				if(localVar.containsKey(ctx.getChild(0).getText() + "isParamList"))
					ucodeInstruction = "lod ";						
				else
					ucodeInstruction = "lda ";
			}
			
			else {
				ident = (String)globalVar.get(ctx.getChild(0).getText());
				
				if(globalVar.containsKey(ctx.getChild(0).getText() + "isParamList"))
					ucodeInstruction = "lod ";						
				else
					ucodeInstruction = "lda ";
			}
			
			newTexts.put(ctx, expr1 + "\n" + space + ucodeInstruction + ident + "\n" + space + "add" + "\n" + expr2 + "\n" + space + "sti");
		}
		
	}

	@Override
	public void exitArgs(MiniCParser.ArgsContext ctx) {
		StringBuffer buf = new StringBuffer();
		
		for(MiniCParser.ExprContext e : ctx.expr()) // expr (',' expr)*
			buf.append(newTexts.get(e) + "\n");
		
		String args = buf + "";
		if(args.length() != 0)
			args = args.substring(0, args.length() - 1); // remove "\n"
		
		newTexts.put(ctx, args);
	}

	private class Node { // Node for make function call tree
		String ident;
		ArrayList<Node> children;
		
		public Node(String ident) {
			this.ident = ident;
			children = new ArrayList<Node>();
		}
		
		public void add(Node child) {
			children.add(child);
		}

		@Override
		public String toString() {
			String tab = "";
			
			for(int j = 0; j < 11-ident.length(); j++)
				tab += " ";
			StringBuffer buf = new StringBuffer(ident + tab);
			
			tab = space;
			if(depth > 1){
				for(int i = 1; i < depth; i ++) {
					tab = tab + " | " + space;
				}
			}
			
			if(children.size() != 0) {
				depth++;
				buf.append("---" + children.get(0) + "\n");
				depth--;
				for(int i = 1; i < children.size(); i++) {
					buf.append(tab + " |-");
					depth++;
					buf.append(children.get(i) + "\n");
					depth--;
				}
				return buf.substring(0, buf.length() - 1);
			}
			return buf.toString();
		}
		
	}
	
}
