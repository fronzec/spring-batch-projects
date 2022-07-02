package com.fronzec.myservice.batch.job1.step3;

import com.fronzec.myservice.batch.persons.ProcessIndicatorItemWrapper;
import com.fronzec.myservice.utils.JsonUtils;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class Step3Writer implements ItemWriter<ProcessIndicatorItemWrapper<PayloadItemInfo>> {

  Logger logger = Logger.getLogger(Step3Writer.class.getName());

  @Override
  public void write(List<? extends ProcessIndicatorItemWrapper<PayloadItemInfo>> items) {
    logger.info("items size to send " + items.size() + JsonUtils.parseObject2Json(items));
  }
}
