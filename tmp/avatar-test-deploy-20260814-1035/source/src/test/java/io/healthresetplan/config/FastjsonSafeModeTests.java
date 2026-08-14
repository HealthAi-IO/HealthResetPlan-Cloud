package io.healthresetplan.config;

import com.alibaba.fastjson.parser.ParserConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FastjsonSafeModeTests {

    @Test
    void safeModeIsEnabledFromApplicationResources() {
        assertThat(ParserConfig.getGlobalInstance().isSafeMode()).isTrue();
    }
}
