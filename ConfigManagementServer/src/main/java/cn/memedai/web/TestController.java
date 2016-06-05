package cn.memedai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by chengtx on 2016/6/3.
 */
@Controller
@RequestMapping("/test")
public class TestController {



    @RequestMapping("test1.do")
    public void test1(HttpServletRequest request, HttpServletResponse response){
        outputData(response,"测试成功test1.do");
        System.out.println("----------test1.do-----------");
    }

    @RequestMapping("test2.do")
    public void test2(HttpServletRequest request, HttpServletResponse response){
        outputData(response,"测试成功test2.do");
        System.out.println("----------test2.do-----------");
    }

    @RequestMapping("test3.do")
    public void test3(HttpServletRequest request, HttpServletResponse response){
        outputData(response,"测试成功test3.do");
        System.out.println("----------test3.do-----------");
    }



    public void outputData(HttpServletResponse response, String data) {
        try{
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().print(data);
            response.getWriter().flush();

        }catch (IOException e){
            e.printStackTrace();
        }

    }

}
