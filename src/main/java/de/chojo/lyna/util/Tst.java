package de.chojo.lyna.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class Tst {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        IntStream.range(0, 3).mapToObj(a -> CompletableFuture.runAsync(() -> {
            //do something
        }, executorService)).toList().forEach(CompletableFuture::join);
    }
}
