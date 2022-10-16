package com.fronzec.singlethreaded.restclients;

import com.fronzec.singlethreaded.batchjobs.job1.step3.PayloadItemInfo;
import java.util.List;
import java.util.Objects;

public class BatchItemsPayload {

  private int itemsSize;

  private List<PayloadItemInfo> items;

  public BatchItemsPayload() {

  }

  public BatchItemsPayload(List<PayloadItemInfo> items) {
    Objects.requireNonNull(items);
    this.itemsSize = items.size();
    this.items = items;
  }

  public int getItemsSize() {
    return itemsSize;
  }

  public void setItemsSize(int itemsSize) {
    this.itemsSize = itemsSize;
  }

  public List<PayloadItemInfo> getItems() {
    return items;
  }

  public void setItems(List<PayloadItemInfo> items) {
    this.items = items;
  }

}