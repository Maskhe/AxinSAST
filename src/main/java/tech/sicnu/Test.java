package tech.sicnu;


import org.apache.commons.lang.StringUtils;
import org.jdom.input.SAXBuilder;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Test {
    public static void main(String[] args) {
        String[] arr = {"0", "1", "2", "3"};
        StringBuffer str = new StringBuffer();
        for (String s : arr) {
            str.append(s);
        }
        System.out.println(str.toString());


        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(3);
        Iterator<Integer> iterator = list.iterator();
        for (Integer integer : list) {
            System.out.println(integer);
        }

        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.toArray()[i]);
        }
        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }

        int[] ints = new int[]{1, 2, 3, 4};
        System.out.println(ints.length);

        List<String> test = Arrays.asList("a", "b");
        try {
            test.remove(0);
        }catch(Exception e){
            System.out.println(e);
        }
        List<String> test2 = new ArrayList<>();
        test2.add("1");
        test2.add("2");
        TestMethod tm = System.out::println;
        tm.print("test");
        int[] b = {1,23};
        List<Integer> c = Arrays.stream(b).boxed().collect(Collectors.toList());

        System.out.println(c.remove(0));
        System.out.println(MessageFormat.format("hello {0}", "world"));
        System.out.println(String.format("hello %s", "world"));

        SAXBuilder saxBuilder = new SAXBuilder();
    }
    interface TestMethod{
        public void print(String str);
    }
}
