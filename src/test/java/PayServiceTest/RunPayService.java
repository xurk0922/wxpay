package PayServiceTest;

import java.net.SocketTimeoutException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import com.jztech.common.Configure;
import com.jztech.common.Signature;
import com.jztech.common.Util;
import com.jztech.protocol.pay_protocol.ScanPayReqData;
import com.jztech.protocol.pay_protocol.ScanPayResData;
import com.jztech.protocol.pay_query_protocol.ScanPayQueryResData;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;

public class RunPayService {
	
	@Test
	public void run() throws Exception {
		String result = null;
		HttpPost httpPost = new HttpPost(Configure.PAY_API);

		// 解决XStream对出现双下划线的bug
		XStream xStreamForRequestPostData = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("-_", "_")));

		// 准备要发送的数据
		ScanPayReqData scanPayReqData = new ScanPayReqData("130090673270387204", "飓卓科技-开发测试", "原样返回",
				"JZ2016092819930922456", 1, "jz001", "60.186.247.144", "20160928151955", "20160928151955", "商品标记");
		scanPayReqData.setAppid("wxf64324660ef204bd");	// 公众号账号
		scanPayReqData.setMch_id("1292934001");	// 商户号
		// 将要提交给API的数据对象转换成XML格式数据Post给API
		String postDataXML = xStreamForRequestPostData.toXML(scanPayReqData);
		// 得指明使用UTF-8编码，否则到API服务器XML的中文不能被成功识别
		StringEntity postEntity = new StringEntity(postDataXML, "UTF-8");
		httpPost.addHeader("Content-Type", "text/xml");
		httpPost.setEntity(postEntity);

		// 设置请求器的配置
		RequestConfig config = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(30000).build();
		httpPost.setConfig(config);

		// 输入提交的api url地址
		System.out.println(httpPost.getRequestLine());

		CloseableHttpClient httpClient = HttpClients.custom().build();
		try {
			HttpResponse response = httpClient.execute(httpPost);

			// 获取响应内容
			HttpEntity entity = response.getEntity();

			result = EntityUtils.toString(entity, "UTF-8");

		} catch (ConnectionPoolTimeoutException e) {
			System.out.println("http get throw ConnectionPoolTimeoutException(wait time out)");

		} catch (ConnectTimeoutException e) {
			System.out.println("http get throw ConnectTimeoutException");

		} catch (SocketTimeoutException e) {
			System.out.println("http get throw SocketTimeoutException");

		} catch (Exception e) {
			System.out.println("http get throw Exception");

		} finally {
			httpPost.abort();
		}
		ScanPayResData scanPayResData = (ScanPayResData) Util.getObjectFromXML(result, ScanPayResData.class);

		if (scanPayResData.getResult_code().equals("FAIL")) {	// 通信失败
			System.out.println("【支付失败】支付API系统返回失败，请检测Post给API的数据是否规范合法");
		} else {
			System.out.println("支付API系统成功返回数据");
			//--------------------------------------------------------------------
            //收到API的返回数据的时候得先验证一下数据有没有被第三方篡改，确保安全
            //--------------------------------------------------------------------
            if (!Signature.checkIsSignValidFromResponseString(result)) {
                System.out.println("【支付失败】支付请求API返回的数据签名验证失败，有可能数据被篡改了");
                return;
            }
          //获取错误码
            String errorCode = scanPayResData.getErr_code();
            //获取错误描述
            String errorCodeDes = scanPayResData.getErr_code_des();
            
            if (scanPayResData.getResult_code().equals("SUCCESS")) {
            	System.out.println("【一次性支付成功】");
            } else {
            	System.out.println("业务返回失败");
            	System.out.println("err_code" + errorCode);
            	System.out.println("err_code_des" + errorCodeDes);
            	
            	//业务错误时错误码有好几种，商户重点提示以下几种
                if (errorCode.equals("AUTHCODEEXPIRE") || errorCode.equals("AUTH_CODE_INVALID") || errorCode.equals("NOTENOUGH")) {

                    //--------------------------------------------------------------------
                    //2)扣款明确失败
                    //--------------------------------------------------------------------

                    //以下几种情况建议明确提示用户，指导接下来的工作
                    if (errorCode.equals("AUTHCODEEXPIRE")) {
                        //表示用户用来支付的二维码已经过期，提示收银员重新扫一下用户微信“刷卡”里面的二维码
                        System.out.println("【支付扣款明确失败】原因是：" + errorCodeDes);
                    } else if (errorCode.equals("AUTH_CODE_INVALID")) {
                        //授权码无效，提示用户刷新一维码/二维码，之后重新扫码支付
                    	System.out.println("【支付扣款明确失败】原因是：" + errorCodeDes);
                    } else if (errorCode.equals("NOTENOUGH")) {
                        //提示用户余额不足，换其他卡支付或是用现金支付
                    	System.out.println("【支付扣款明确失败】原因是：" + errorCodeDes);
                    }
                } else if (errorCode.equals("USERPAYING")) {

                    //--------------------------------------------------------------------
                    //3)需要输入密码
                    //--------------------------------------------------------------------

                    //表示有可能单次消费超过300元，或是免输密码消费次数已经超过当天的最大限制，这个时候提示用户输入密码，商户自己隔一段时间去查单，查询一定次数，看用户是否已经输入了密码
                    System.out.println("【需要用户输入密码、查询到支付成功】");
                } else {

                    //--------------------------------------------------------------------
                    //4)扣款未知失败
                    //--------------------------------------------------------------------
                	ScanPayQueryResData scanPayQueryResData = (ScanPayQueryResData) Util.getObjectFromXML(result, ScanPayQueryResData.class);
                    if (scanPayQueryResData.getTrade_state().equals("SUCCESS")) {
                        System.out.println("【支付扣款未知失败、查询到支付成功】");
                    } else {
                    	System.out.println("【支付扣款未知失败、在一定时间内没有查询到支付成功");
                    }
                }
            }
		}
		
	}
}
