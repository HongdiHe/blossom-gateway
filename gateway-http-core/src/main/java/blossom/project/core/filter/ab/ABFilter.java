package blossom.project.core.filter.ab;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import static blossom.project.common.constant.FilterConst.*;

@FilterAspect(id = GRAY_FILTER_ID,
        name = GRAY_FILTER_NAME,
        order = GRAY_FILTER_ORDER)
public class ABFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ABFilter.class);

    public static final String GRAY = "true";
    @Override
    public void doFilter(GatewayContext ctx) throws Exception {
        //测试灰度功能待时候使用  我们可以手动指定其是否为灰度流量
        String ab_testing_group = ctx.getRequest().getHeaders().get("ab_testing_group");
        ctx.setAbTestingGroup(ab_testing_group);
        //选取部分的灰度用户
        String clientIp = ctx.getRequest().getClientIp();
        //等价于对1024取模
        int res = clientIp.hashCode() & (1024 - 1);
        if (res == 1) {
            //1024分之一的概率
            ctx.setGray(true);
        }

    }
}
