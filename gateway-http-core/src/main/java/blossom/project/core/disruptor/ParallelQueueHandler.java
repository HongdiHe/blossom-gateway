package blossom.project.core.disruptor;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @param <E> The type of events stored in the queue.
 * This class implements a lock-free multi-producer multi-consumer queue based on Disruptor.
 * Its main function is to use the Disruptor framework to achieve a high-performance event processing queue.
 * It utilizes some core concepts of Disruptor, such as RingBuffer, WaitStrategy, and WorkerPool,
 * and provides flexible parameter configuration through the Builder pattern.
 */
public class ParallelQueueHandler<E> implements ParallelQueue<E> {

    /**
     * The RingBuffer is used as an internal buffer to store event Holder objects.
     */
    private RingBuffer<Holder> ringBuffer;

    /**
     * The event listener for handling events.
     */
    private EventListener<E> eventListener;

    /**
     * The worker pool for handling events in parallel.
     */
    private WorkerPool<Holder> workerPool;

    /**
     * The thread pool for executing tasks.
     */
    private ExecutorService executorService;

    /**
     * An interface in the Disruptor framework used to populate event objects with data when publishing events.
     */
    private EventTranslatorOneArg<Holder, E> eventTranslator;

    /**
     * Constructor to initialize the Disruptor queue using the Builder pattern.
     *
     * @param builder The builder for the Disruptor queue.
     */
    public ParallelQueueHandler(Builder<E> builder) {
        this.executorService = Executors.newFixedThreadPool(builder.threads,
                new ThreadFactoryBuilder().setNameFormat("ParallelQueueHandler" + builder.namePrefix + "-pool-%d").build());

        this.eventListener = builder.listener;
        this.eventTranslator = new HolderEventTranslator();

        // Create the RingBuffer
        RingBuffer<Holder> ringBuffer = RingBuffer.create(builder.producerType, new HolderEventFactory(),
                builder.bufferSize, builder.waitStrategy);

        // Create a sequence barrier from the RingBuffer (a fixed process)
        SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        // Create multiple consumer groups
        WorkHandler<Holder>[] workHandlers = new WorkHandler[builder.threads];
        for (int i = 0; i < workHandlers.length; i++) {
            workHandlers[i] = new HolderWorkHandler();
        }

        // Create a multi-consumer worker pool
        WorkerPool<Holder> workerPool = new WorkerPool<>(ringBuffer, sequenceBarrier, new HolderExceptionHandler(),
                workHandlers);
        // Set the sequences of multi-consumers, mainly used for statistics of consumption progress
        ringBuffer.addGatingSequences(workerPool.getWorkerSequences());
        this.workerPool = workerPool;
    }

    /**
     * Add an event to the queue.
     *
     * @param event The event to be added.
     */
    @Override
    public void add(E event) {
        final RingBuffer<Holder> holderRing = ringBuffer;
        if (holderRing == null) {
            process(this.eventListener, new IllegalStateException("ParallelQueueHandler is closed"), event);
        }
        try {
            ringBuffer.publishEvent(this.eventTranslator, event);
        } catch (NullPointerException e) {
            process(this.eventListener, new IllegalStateException("ParallelQueueHandler is closed"), event);
        }
    }

    /**
     * Add multiple events to the queue.
     *
     * @param events The array of events to be added.
     */
    @Override
    public void add(E... events) {
        final RingBuffer<Holder> holderRing = ringBuffer;
        if (holderRing == null) {
            process(this.eventListener, new IllegalStateException("ParallelQueueHandler is closed"), events);
        }
        try {
            ringBuffer.publishEvents(this.eventTranslator, events);
        } catch (NullPointerException e) {
            process(this.eventListener, new IllegalStateException("ParallelQueueHandler is closed"), events);
        }
    }

    /**
     * Try to add an event to the queue.
     *
     * @param event The event to be added.
     * @return true if the event is successfully added, false otherwise.
     */
    @Override
    public boolean tryAdd(E event) {
        final RingBuffer<Holder> holderRing = ringBuffer;
        if (holderRing == null) {
            return false;
        }
        try {
            return ringBuffer.tryPublishEvent(this.eventTranslator, event);
        } catch (NullPointerException e) {
            return false;
        }
    }

    /**
     * Try to add multiple events to the queue.
     *
     * @param events The array of events to be added.
     * @return true if all events are successfully added, false otherwise.
     */
    @Override
    public boolean tryAdd(E... events) {
        final RingBuffer<Holder> holderRing = ringBuffer;
        if (holderRing == null) {
            return false;
        }
        try {
            return ringBuffer.tryPublishEvents(this.eventTranslator, events);
        } catch (NullPointerException e) {
            return false;
        }
    }

    /**
     * Start the queue.
     */
    @Override
    public void start() {
        this.ringBuffer = workerPool.start(executorService);
    }

    /**
     * Shut down the queue.
     */
    @Override
    public void shutDown() {
        RingBuffer<Holder> holder = ringBuffer;
        ringBuffer = null;
        if (holder == null) {
            return;
        }
        if (workerPool != null) {
            workerPool.drainAndHalt();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Check if the queue is shut down.
     *
     * @return true if the queue is shut down, false otherwise.
     */
    @Override
    public boolean isShutDown() {
        return ringBuffer == null;
    }

    /**
     * A static method for handling exceptions by invoking the exception handling method of the event listener.
     *
     * @param listener The event listener.
     * @param e        The exception.
     * @param event    The event.
     * @param <E>      The type of the event.
     */
    private static <E> void process(EventListener<E> listener, Throwable e, E event) {
        listener.onException(e, -1, event);
    }

    /**
     * A static method for handling exceptions by invoking the exception handling method of the event listener for multiple events.
     *
     * @param listener The event listener.
     * @param e        The exception.
     * @param events   The array of events.
     * @param <E>      The type of the events.
     */
    private static <E> void process(EventListener<E> listener, Throwable e, E... events) {
        for (E event : events) {
            process(listener, e, event);
        }
    }

    /**
     * The Builder pattern for configuring the ParallelQueueHandler.
     *
     * @param <E> The type of events stored in the queue.
     */
    public static class Builder<E> {
        /**
         * The producer type, default is multi-producer type.
         */
        private ProducerType producerType = ProducerType.MULTI;
        /**
         * The size of the thread queue.
         */
        private int bufferSize = 1024 * 16;
        /**
         * The number of worker threads, default is 1.
         */
        private int threads = 1;
        /**
         * The prefix for thread names, used for module identification.
         */
        private String namePrefix = "";
        /**
         * The wait strategy for the Disruptor.
         */
        private WaitStrategy waitStrategy = new BlockingWaitStrategy();
        /**
         * The event listener.
         */
        private EventListener<E> listener;

        /**
         * Set the producer type.
         *
         * @param producerType The producer type.
         * @return The builder instance.
         */
        public Builder<E> setProducerType(ProducerType producerType) {
            Preconditions.checkNotNull(producerType);
            this.producerType = producerType;
            return this;
        }

        /**
         * Set the buffer size. The buffer size must be a power of 2.
         *
         * @param bufferSize The buffer size.
         * @return The builder instance.
         */
        public Builder<E> setBufferSize(int bufferSize) {
            Preconditions.checkArgument(Integer.bitCount(bufferSize) == 1);
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * Set the number of worker threads. The number must be greater than 0.
         *
         * @param threads The number of worker threads.
         * @return The builder instance.
         */
        public Builder<E> setThreads(int threads) {
            Preconditions.checkArgument(threads > 0);
            this.threads = threads;
            return this;
        }

        /**
         * Set the name prefix for threads.
         *
         * @param namePrefix The name prefix.
         * @return The builder instance.
         */
        public Builder<E> setNamePrefix(String namePrefix) {
            Preconditions.checkNotNull(namePrefix);
            this.namePrefix = namePrefix;
            return this;
        }

        /**
         * Set the wait strategy.
         *
         * @param waitStrategy The wait strategy.
         * @return The builder instance.
         */
        public Builder<E> setWaitStrategy(WaitStrategy waitStrategy) {
            Preconditions.checkNotNull(waitStrategy);
            this.waitStrategy = waitStrategy;
            return this;
        }

        /**
         * Set the event listener.
         *
         * @param listener The event listener.
         * @return The builder instance.
         */
        public Builder<E> setListener(EventListener<E> listener) {
            Preconditions.checkNotNull(listener);
            this.listener = listener;
            return this;
        }

        /**
         * Build the ParallelQueueHandler object.
         *
         * @return The constructed ParallelQueueHandler object.
         */
        public ParallelQueueHandler<E> build() {
            return new ParallelQueueHandler<>(this);
        }
    }

    /**
     * The event holder class.
     */
    public class Holder {
        /**
         * The event stored in the holder.
         */
        private E event;

        /**
         * Set the value of the event.
         *
         * @param event The event to be set.
         */
        public void setValue(E event) {
            this.event = event;
        }

        /**
         * Override the toString method for debugging purposes.
         *
         * @return A string representation of the Holder object.
         */
        @Override
        public String toString() {
            return "Holder{" + "event=" + event + '}';
        }
    }

    /**
     * The exception handler for handling exceptions during event processing.
     */
    private class HolderExceptionHandler implements ExceptionHandler<Holder> {

        @Override
        public void handleEventException(Throwable throwable, long l, Holder event) {
            Holder holder = (Holder) event;
            try {
                eventListener.onException(throwable, l, holder.event);
            } catch (Exception e) {
                // Additional handling can be added here if an exception occurs during exception handling.
            } finally {
                holder.setValue(null);
            }
        }

        @Override
        public void handleOnStartException(Throwable throwable) {
            throw new UnsupportedOperationException(throwable);
        }

        @Override
        public void handleOnShutdownException(Throwable throwable) {
            throw new UnsupportedOperationException(throwable);
        }
    }

    /**
     * The work handler for consumers to process events.
     */
    private class HolderWorkHandler implements WorkHandler<Holder> {
        @Override
        public void onEvent(Holder holder) throws Exception {
            // Invoke the event handling method of the event listener.
            eventListener.onEvent(holder.event);
            // Set the event to null after processing to help with garbage collection.
            holder.setValue(null);
        }
    }

    /**
     * The event factory for creating event Holder objects.
     */
    private class HolderEventFactory implements EventFactory<Holder> {

        @Override
        public Holder newInstance() {
            return new Holder();
        }
    }

    /**
     * The event translator for populating event data into the Holder object.
     */
    private class HolderEventTranslator implements EventTranslatorOneArg<Holder, E> {
        @Override
        public void translateTo(Holder holder, long l, E e) {
            // Populate the event data into the Holder object.
            holder.setValue(e);
        }
    }
}