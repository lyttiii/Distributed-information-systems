package ru.ris.crackhash.worker.service;

import jakarta.annotation.PreDestroy;
import org.paukov.combinatorics3.Generator;
import org.springframework.stereotype.Service;
import ru.ris.crackhash.contract.xml.ManagerWorkerCrackRequest;
import ru.ris.crackhash.contract.xml.WorkerManagerCrackResponse;
import ru.ris.crackhash.worker.util.Md5Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WorkerCrackService {

    private final ManagerCallbackClient managerCallbackClient;
    private final ExecutorService workerExecutor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final Set<String> canceledRequests = ConcurrentHashMap.newKeySet();

    public WorkerCrackService(ManagerCallbackClient managerCallbackClient) {
        this.managerCallbackClient = managerCallbackClient;
    }

    public void submitTask(ManagerWorkerCrackRequest task) {
        if (task == null || task.getRequestId() == null) {
            return;
        }

        if (canceledRequests.contains(task.getRequestId())) {
            canceledRequests.remove(task.getRequestId());
            return;
        }

        AtomicBoolean cancelFlag = cancelFlags.computeIfAbsent(task.getRequestId(), ignored -> new AtomicBoolean(false));
        if (cancelFlag.get()) {
            cancelFlags.remove(task.getRequestId(), cancelFlag);
            canceledRequests.remove(task.getRequestId());
            return;
        }

        workerExecutor.submit(() -> processTask(task, cancelFlag));
    }

    public void cancelTask(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        canceledRequests.add(requestId);
        AtomicBoolean flag = cancelFlags.get(requestId);
        if (flag != null) {
            flag.set(true);
        }
    }

    public void cancelAllTasks() {
        canceledRequests.addAll(cancelFlags.keySet());
        cancelFlags.values().forEach(flag -> flag.set(true));
    }

    private void processTask(ManagerWorkerCrackRequest task, AtomicBoolean cancelFlag) {
        WorkerManagerCrackResponse response = new WorkerManagerCrackResponse();
        response.setRequestId(task.getRequestId());
        response.setPartNumber(task.getPartNumber());

        try {
            List<String> answers = crackHash(task, cancelFlag);
            if (isCancelled(task.getRequestId(), cancelFlag)) {
                return;
            }
            response.setAnswers(answers);
        } catch (Exception ex) {
            if (isCancelled(task.getRequestId(), cancelFlag)) {
                return;
            }
            response.setFailed(true);
            response.setErrorMessage("Worker execution failed");
        } finally {
            cancelFlags.remove(task.getRequestId(), cancelFlag);
            canceledRequests.remove(task.getRequestId());
        }

        managerCallbackClient.sendResult(response);
    }

    private List<String> crackHash(ManagerWorkerCrackRequest task, AtomicBoolean cancelFlag) {
        List<String> results = new ArrayList<>();

        String targetHash = task.getHash().toLowerCase();
        List<String> alphabet = toAlphabet(task.getAlphabet());
        int maxLength = task.getMaxLength();

        long totalWords = calculateTotalWords(alphabet.size(), maxLength);
        long[] range = calculateRange(totalWords, task.getPartNumber(), task.getPartCount());

        long rangeStart = range[0];
        long rangeEndExclusive = range[1];

        long consumed = 0L;
        for (int length = 1; length <= maxLength; length++) {
            if (isCancelled(task.getRequestId(), cancelFlag)) {
                return results;
            }

            long lengthCount = pow(alphabet.size(), length);
            long lengthStart = consumed;
            long lengthEnd = lengthStart + lengthCount;

            if (rangeEndExclusive <= lengthStart) {
                break;
            }
            if (rangeStart >= lengthEnd) {
                consumed = lengthEnd;
                continue;
            }

            long localFrom = Math.max(0, rangeStart - lengthStart);
            long localTo = Math.min(lengthCount, rangeEndExclusive - lengthStart);

            long index = 0;
            for (List<String> vector : Generator.permutation(alphabet).withRepetitions(length)) {
                if (isCancelled(task.getRequestId(), cancelFlag)) {
                    return results;
                }

                if (index >= localTo) {
                    break;
                }

                if (index >= localFrom) {
                    String candidate = toWord(vector);
                    if (Md5Util.md5Hex(candidate).equals(targetHash)) {
                        results.add(candidate);
                    }
                }
                index++;
            }

            consumed = lengthEnd;
        }

        return results;
    }

    private List<String> toAlphabet(String source) {
        List<String> letters = new ArrayList<>(source.length());
        for (char c : source.toCharArray()) {
            letters.add(String.valueOf(c));
        }
        return letters;
    }

    private long[] calculateRange(long totalWords, int partNumber, int partCount) {
        long baseSize = totalWords / partCount;
        long remainder = totalWords % partCount;

        long start = partNumber * baseSize + Math.min(partNumber, remainder);
        long size = baseSize + (partNumber < remainder ? 1 : 0);
        long endExclusive = start + size;

        return new long[]{start, endExclusive};
    }

    private long calculateTotalWords(int alphabetSize, int maxLength) {
        long total = 0L;
        for (int length = 1; length <= maxLength; length++) {
            total += pow(alphabetSize, length);
        }
        return total;
    }

    private long pow(int base, int exp) {
        long result = 1L;
        for (int i = 0; i < exp; i++) {
            result = Math.multiplyExact(result, base);
        }
        return result;
    }

    private String toWord(List<String> vector) {
        StringBuilder builder = new StringBuilder(vector.size());
        for (String symbol : vector) {
            builder.append(symbol);
        }
        return builder.toString();
    }

    private boolean isCancelled(String requestId, AtomicBoolean cancelFlag) {
        return Thread.currentThread().isInterrupted()
                || cancelFlag.get()
                || canceledRequests.contains(requestId);
    }

    @PreDestroy
    public void shutdownExecutor() {
        workerExecutor.shutdownNow();
    }
}
