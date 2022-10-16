package com.fronzec.singlethreaded.batchjobs.persons;

/**
 * Item wrapper useful in "process indicator" use cases, where input is marked as processed
 * by the processor/writer. This requires passing a technical identifier of the input data
 * so that it can be modified in later stages.
 *
 * @param <T> item type
 */
public class ProcessIndicatorItemWrapper<T> {

  private final long id;

  private final T item;

  public ProcessIndicatorItemWrapper(long id, T item) {
    this.id = id;
    this.item = item;
  }

  public long getId() {
    return id;
  }

  public T getItem() {
    return item;
  }
}
