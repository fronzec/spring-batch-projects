package com.fronzec.singlethreaded;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiKeyMap;

public class Test {
    public static void main(String[] args) {
        // crea un mapa ordenado
        MultiKeyMap multiKeyMap = MultiKeyMap.multiKeyMap(new LinkedMap());

        // [key1, key2] -> value1
        multiKeyMap.put("key1", 1L, "value1");

        // [key3, key4] -> value2
        multiKeyMap.put("key1", 2L, "value2");
        multiKeyMap.put("key2", 1L, "value3");
        multiKeyMap.put("key2", 2L, "value4");

        // imprimir mapa multiclave
        System.out.println(multiKeyMap);

        // imprime el valor correspondiente a key1 y key2
        String key1 = (String)multiKeyMap.get("key1", 1L);
        System.out.println(key1);
        System.out.println(multiKeyMap.containsKey("key1", 0L));
    }
}
