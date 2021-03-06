package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;

@Controller
public class UserController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"login","login.html"})
    public String login(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:index";
        } else
            return "login";
    }
    @GetMapping({"register","register.html"})
    public String register(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:index";
        } else
            return "register";
    }
    @GetMapping({"forgot-password","forgot-password.html"})
    public String forgotPassword(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:index";
        } else
            return "forgot-password";
    }

    @GetMapping("userLogin")
    public String userLogin(@RequestParam("username") String username,
                            @RequestParam("password") String password,
                            @RequestParam(value = "remember", required = false) String remember,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            String sql = String.format("SELECT * FROM user_info WHERE username = \"%s\"", username);
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            if (password.equals(result.get("password"))) {
                session.setAttribute("currentUser", username);
                session.setAttribute("currentUserId", result.get("id")); //数据类型为long
                session.setAttribute("currentUserType", result.get("user_type"));

                if (remember != null) {
                    session.setMaxInactiveInterval(86400);
                }
                return "redirect:index";
            }
            else {
                redirectAttributes.addFlashAttribute("message", "密码错误");
                return "redirect:login";
            }
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "用户不存在或用户名输入错误！");
            return "redirect:login";
        }
    }

    @GetMapping("userLogout")
    public String userLogout(HttpSession session) {
        session.invalidate();
        return "redirect:login";
    }

    @GetMapping("userRegister")
    public String userRegister(@RequestParam("username") String username,
                               @RequestParam("email") String email,
                               @RequestParam("password") String password,
                               @RequestParam("repeatPassword") String repeatPassword,
                               @RequestParam("type") String type,
                               RedirectAttributes redirectAttributes) {
        String emailRule = "[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$";
        if (username.equals("")) {
            redirectAttributes.addFlashAttribute("message", "注册失败：用户名不能为空");
            return "redirect:register";
        }
        if (email.equals("")) {
            redirectAttributes.addFlashAttribute("message", "注册失败：邮箱不能为空");
            return "redirect:register";
        }
        if (!email.matches(emailRule)) {
            redirectAttributes.addFlashAttribute("message", "注册失败：邮箱格式不正确");
            return "redirect:register";
        }
        if (password.equals("")) {
            redirectAttributes.addFlashAttribute("message", "注册失败：密码不能为空");
            return "redirect:register";
        }
        if (!password.equals(repeatPassword)) {
            redirectAttributes.addFlashAttribute("message", "注册失败：两次密码输入不一致");
            return "redirect:register";
        }

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            String sql = String.format("INSERT INTO user_info(user_type, username, password, email, register_date) VALUES(\"%s\", \"%s\", \"%s\", \"%s\", \"%s\")",
                    type, username, password, email, sdf.format(date));
            jdbcTemplate.update(sql);
        }
        catch (DuplicateKeyException e) {
            redirectAttributes.addFlashAttribute("message", "注册失败：用户名已被占用");
        }

        redirectAttributes.addFlashAttribute("message", "注册成功，欢迎！");
        return "redirect:login";
    }

    @Autowired
    private JavaMailSender javaMailSender;

    @GetMapping("resetPassword")
    public String resetPassword(@RequestParam("username") String username,
                                @RequestParam("code") String code,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("action") String action,
                                RedirectAttributes redirectAttributes) {
        String sql, email;
        try {
            sql = String.format("SELECT email FROM user_info WHERE username = \"%s\"", username);
            email = (String)jdbcTemplate.queryForObject(sql, String.class);
        }
        catch (EmptyResultDataAccessException e) {
            redirectAttributes.addFlashAttribute("message", "用户不存在");
            return "redirect:forgot-password";
        }

        switch (action) {
            case "send":
                SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
                simpleMailMessage.setFrom("xm06_scu_class@163.com");
                simpleMailMessage.setTo(email);

                Random random = new Random();
                int verificationCode;
                do {
                    verificationCode = (int)(random.nextDouble() * 1000000);
                } while (verificationCode < 100000);

                simpleMailMessage.setSubject("SCU Course 重置密码身份验证");
                simpleMailMessage.setText("您好，验证码是 " + verificationCode);

                sql = String.format("INSERT INTO verification_info(username,email,code) VALUES(\"%s\",\"%s\",\"%d\")",
                        username, email, verificationCode);
                jdbcTemplate.update(sql);

                javaMailSender.send(simpleMailMessage);

                redirectAttributes.addFlashAttribute("lastUsername", username);
                return "redirect:forgot-password";

            case "reset":
                try {
                    sql = String.format("SELECT code FROM verification_info WHERE email = \"%s\"", email);
                    String code_sent = jdbcTemplate.queryForObject(sql, String.class);

                    if (code_sent.equals(code)) {
                        sql = String.format("UPDATE user_info SET password = \"%s\" WHERE username = \"%s\"", newPassword, username);
                        jdbcTemplate.update(sql);
                    }
                    sql = String.format("DELETE FROM verification_info WHERE username = \"%s\"", username);
                    jdbcTemplate.update(sql);

                    redirectAttributes.addFlashAttribute("message", "密码重置成功！");
                    return "redirect:login";
                }
                catch (NullPointerException e) {
                    redirectAttributes.addFlashAttribute("message", "尚未发送验证码");
                    return "redirect:forgot-password";
                }
        }
        return "redirect:forgot-password";
    }
}
