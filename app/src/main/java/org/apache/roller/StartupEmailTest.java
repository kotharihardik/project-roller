// package org.apache.roller;

// import javax.servlet.ServletContextEvent;
// import javax.servlet.ServletContextListener;
// import javax.servlet.annotation.WebListener;

// import org.apache.roller.weblogger.util.MailUtil;

// @WebListener
// public class StartupEmailTest implements ServletContextListener {

//     @Override
//     public void contextInitialized(ServletContextEvent sce) {

//         System.out.println("========= ROLLER STARTUP EMAIL TEST =========");

//         try {

//             MailUtil.sendHTMLMessage(
//                     "hardikkothari300@gmail.com",                 // FROM
//                     new String[]{"hardikkothari300@gmail.com"},   // TO
//                     null,                                         // CC
//                     null,                                         // BCC
//                     "Roller Email Test",                          // SUBJECT
//                     "<h2>Roller MailUtil Test</h2>"
//                     + "<p>Email sent through Roller MailUtil.</p>"
//             );

//             System.out.println("========= MAILUTIL EMAIL SENT SUCCESS =========");

//         } catch (Exception e) {

//             System.out.println("========= MAILUTIL EMAIL FAILED =========");
//             e.printStackTrace();

//         }
//     }

//     @Override
//     public void contextDestroyed(ServletContextEvent sce) {
//     }
// }

