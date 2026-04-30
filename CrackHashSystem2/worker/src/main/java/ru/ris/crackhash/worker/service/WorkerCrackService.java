package ru.ris.crackhash.worker.service;

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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class WorkerCrackService {

    private final ConcurrentMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final Set<String> canceledRequests = ConcurrentHashMap.newKeySet();

    public void cancelTask(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        canceledRequests.add(requestId);
        String prefix = requestId + ":";
        for (var entry : cancelFlags.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().set(true);
            }
        }
    }

    public void cancelAllTasks() {
        for (String taskKey : cancelFlags.keySet()) {
            int separator = taskKey.indexOf(':');
            if (separator > 0) {
                canceledRequests.add(taskKey.substring(0, separator));
            }
        }
        cancelFlags.values().forEach(flag -> flag.set(true));
    }

    public WorkerManagerCrackResponse executeTask(ManagerWorkerCrackRequest task) {
        if (task == null || task.getRequestId() == null || task.getRequestId().isBlank()) {
            return null;
        }

        String requestId = task.getRequestId();
        if (canceledRequests.contains(requestId)) {
            return null;
        }

        String taskKey = toTaskKey(task);
        AtomicBoolean cancelFlag = cancelFlags.computeIfAbsent(taskKey, ignored -> new AtomicBoolean(false));
        if (cancelFlag.get()) {
            cancelFlags.remove(taskKey, cancelFlag);
            return null;
        }

        WorkerManagerCrackResponse response = new WorkerManagerCrackResponse();
        response.setRequestId(requestId);
        response.setPartNumber(task.getPartNumber());

        try {
            List<String> answers = crackHash(task, cancelFlag);
            if (isCancelled(requestId, cancelFlag)) {
                return null;
            }
            response.setAnswers(answers);
        } catch (Exception ex) {
            if (isCancelled(requestId, cancelFlag)) {
                return null;
            }
            response.setFailed(true);
            response.setErrorMessage("Worker execution failed");
        } finally {
            cancelFlags.remove(taskKey, cancelFlag);
        }

        return response;
    }

    public boolean isCancellationRequested(String requestId) {
        return requestId != null && canceledRequests.contains(requestId);
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

    private String toTaskKey(ManagerWorkerCrackRequest task) {
        return task.getRequestId() + ":" + task.getPartNumber();
    }
}
