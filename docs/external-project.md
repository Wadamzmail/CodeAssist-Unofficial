# How to open external project in Code Assist Pro | Android 11+

In this file, you will find infomation about how to setup or open project from external file system (out of app sandbox enviroment) in code assist pro.

> [!NOTE]
> Following, may or may not be able to apply to any other code assist flavours (or code assist download from other sources than the [Wadamzmail/CodeAssist-Unofficial](https://github.com/Wadamzmail/CodeAssist-Unofficial)).

## Setup of external project in Code Assist Pro.

`TL;DR`: Setup project like normally, Export the project as zip file. Delete The project from code assist. Unzip the export project In settings, toggle the custom project. From file explorer. Open the project folder in codeassist pro app.

[Vidoe Toutrial](https://youtube.com/shorts/3buaY_T0KTM?si=Asg8eGacg0xj8Gyh)

1) Download code assist pro from [Wadamzmail/CodeAssist-Unofficial](https://github.com/Wadamzmail/CodeAssist-Unofficial).

2) Create a new project in code assit pro. Enter your details like appname, apppackagename, language, minsdk and don;t touch save location path, let it be in app sandbox enviroment.

3) Once the project is setup, and code assist pro is done with its "indexing". Close the project by back gesture.

4) You will find yourself on main screen, now click on `project manager`.

5) Now find your newly create project in bottom sheet and do a long press (click) on it.

6) You will find a dialog open in front of you, select export project option. and export the file at your desired location in zip file. (Tip: prefer `downloads` folder)

7) You will find yourself on main screen, now click on `project manager`.

8) Now find your newly create project in bottom sheet and do a long press (click) on it.

9) You will find a dialog open in front of you, select delete option. and delete the project.

10) Close code assist pro and open device file manager.

11) Now, open the path in device file manager, where you exported the project and unzip the file.

12) Then, open code assist pro app again. goto setting in code assist pro.

13) In project, enable custom project option.

14) Navigate back to main screen, and you will see open project instead of project manager.

15) Click on open project. you will find yourself on file picker.

16) Navigate to app folder, that you previously unzip.

17) Now select the folder & open it in code assist pro.

> [!WARNING]
> Be careful to open the folder that is parent of app directory,
> in otherwords the folder, you select should be your project root containing app folder directly (as sub directory),
> otherwise you might get wired errors.
