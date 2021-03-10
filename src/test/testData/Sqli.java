import org.apache.commons.lang3.StringUtils;
import java.text.MessageFormat;
public class Sqli {
    public static void main(String id, String[] ids, String[] columns){
         TestObj  testObj = new TestObj();
         String sql1 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from test where id = " + id</error>;
        String sql2 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from test where id in ("+StringUtils.join(ids, ",")+")"</error>;
        String table = "test";
        table += "1";
        String sql3 = "select * from "+table;
        String sql4 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from " + getTable()</error>;
        String sql5 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from " + getTable2("123")</error>;
        String sql6 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from "  + getTable3()</error>;
        table = getTable2("123");
        String sql7 =  <error descr="AxinSAST: SQL Injection Found!!!">"select * from " + table</error>;
        String sql8 = <error descr="AxinSAST: SQL Injection Found!!!">"select " + StringUtils.join(columns, ",") + " from test"</error>;
        String sql9 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from "+testObj.id</error>;
        String sql10 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from "+testObj.table</error>;
        String id3 = table + " and 1";
        id3 += "123";
        id3 = table;
        String sql11 = <error descr="AxinSAST: SQL Injection Found!!!">"select * from table where id=" + id3</error>;
        String template1 = "select id from table where id=%s";
        String sql12 = <error descr="AxinSAST: SQL Injection Found!!!">String.format(template1, id)</error>;
        String sql13 = <error descr="AxinSAST: SQL Injection Found!!!">String.format("select id from table where id=%s", id)</error>;
        String sql14 = <error descr="AxinSAST: SQL Injection Found!!!">MessageFormat.format("select id from table where id={0}", id)</error>;
    }

    public static String getTable(){
        if(true){
            return "test";
        }
        return "test";
    }

    public static String getTable2(String str){
        String table = "test";
        table += str;
        return table;
    }

    public static String getTable3(){
        String  table = "test";
        return table;
    }


}

class TestObj{
    public String id = "123";
    public String table;
}