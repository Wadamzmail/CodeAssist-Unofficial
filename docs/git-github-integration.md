# How to integrate Git & Github with Code Assist Pro.

In this file, you will find information about, how to integrated git & github into code assist pro. 

> [!NOTE]
> This docs is specific for latest version of Code Assist Pro (Wadamzmail/CodeAssist-Unofficial).
> 
> This docs may or may not work for other code assist flavour.

## Prerequisites

- Obviously, An account on [GitHub](https://github.com). Here, Is how to [create one](https://docs.github.com/en/get-started/start-your-journey/creating-an-account-on-github) if don't have it yet.

## Overview

There are three ways to use github with your code assist pro project. we will only discussed here, the one that integrates with codeassist pro directly...

- Using github website to upload code.. (beginner friendly, non-standard way)
- Using termux, git & codeassist pro.. (standard way, exact way we do on laptops & pc)
- Using code assist way.. (hard setup, but became easier overtime)

## Moveing your project from phone to cloud (GitHub)

> [!NOTE]
> This guide is made by keep absolutely beginner in mind, so if you know GitHub, you might find terms like upload or moving instead of push or pull. so please ignore them.

Here is inform of, How you setup Code Assist Pro.

> Let get your project open source or maybe just as a private repository on github.

## Overall Steps

As you have guess, uploading (pushing) your project to GitHub is hard task, and you are correct to some degree. But don't worry or get overwhelmed by steps they are just to give you a idea of what you need to do...

- Setup your git credentials on Codeassist Pro.
- Setup your code assist pro project for github & git.
- Uploading your project to github.
- Updating your project on github again. (after change your code)
- Learn & Improve Github best practices, became a better developer and if possible open source contributor.

### Setup your git credentials on Codeassist Pro

1. Open code assist pro app.

2. Navigate to settings in code assist pro app. by clicking on to configure settings.

3. Now, select git source control

4. Now, click on user name and enter your full name. (preferably: `firstname lastname`)

5. Now click on user email and enter your email. (preferably: The one associate with github account)

6. Now, your github credentials is setup. you move further with project setup.

### Setup your project for Git+Github.

Assume that you have already setup git credentials if not read above sections. 

1. Open codeassist pro app & then in code assist pro open the project you wish to upload to github.

2. Once you open desired project, you find yourself in ide. Now, click on the folder icon in top app bar. next to three dots icon.

3. You will see a drop down, select git from the drop down. (last option)

4. You find yourself prompt with dialog with many option, select the init (mostly like it will be first option)

5. You will see a message (toast), saying the empty git repository has initialised at project path..
	
	-  To confirm & to celebrate, open the file explorer from left side, inside code assist. refresh the files and you will see `.git` folder under your project directory. This will make your project a git repository. and git setup is complete from here. 

> [!WARNING]
> Now we only need to take care of github which is kind of hard.. Stay little bit more focus from now, Read the step and follow only if you understand. I recommend reading all the steps first then coming back and read and follow each step again.

6. We are done with code assist pro, Go to browser (preferably chrome). open [github.com](https://github.com/new) and create a repository.

7. Enter only required details as in, name of repository (aka project name) & visibility (public or private) & hit create repository. I repeat nothing else, should be touched.. and should be left as default.

> [!IMPORTANT]
> Don't tick the add readme, select no .gitignore, no license. Don't change this you will get into problems... They can be change later. These options will be select by default so don't touch it.

8. Now scroll down and you find section call quick setup (mostly it highlighted in blue).

9. Now in it, you will find two options HTTPS & SSH. you also find URL next to it. but that url is in http we need a ssh url.

10. Now, select the ssh and copy the url next to it.

	-  Quick Check: paste the think you just copy and check that it something like `git@github.com` and not `https://github.com`

11. Now with ssh url copied move into code assist pro then into your project and into git. (you already know how to do that from above)

12. If did navigate correct you will see all git options.. select remote from the dialog. paste the ssh url there... it should start with `git@github.com`

13. Click add and you see a message remote add successfully.

14. We not done yet, there still something left most important thing. give code assist pro access to github..

15. In project, click three dots on the top left bar, then select ssh manager.

16. If you did everything things successfully you will see SSH Key Manager in app bar. and will see a fab on bottom right..

17. Click that plus sign (+), FAB and you see a dialog.. generate key.

18. Enter key name.. if you don't what you are doing enter (`codeasssit_pro_github_access_ssh_key`)

19. Now, generate the key.. and you will see key is load as message and also will see two items in ssh key manager.

20. Be careful ssh key are like password (a super secure one) so, don't share them anywhere. (ssh keys are like passwords generate and use by computers or atleast you can say that)

21. Now, do a long press on the public key... the one in purple and has pub in its name and icon.

22. Now, select show content & copy the alien stuff (long text) in front of you...

23. Close the code assist pro app. and now into your browser, at https://github.com/settings/keys (ensure first that you are logged in).

24. scroll down on the page and you see a section call ssh key. hit new ssh key button. or vist https://github.com/settings/ssh/new

25. Paste that alien content you copied earlier into key. yes the into that big box that say key.

26. Now give it a title. (preferably `Code Assist Pro GitHub Access`)

27. In key type select authentication key, mostly it will selected by default.

28. And hit add ssh key.. GitHub will mostly ask you to confirm that its you how is doing the stuff... Because it that important..

29. After confirming its you the one. github will accept your key..  and it will be reflected by you see the newly added key in ssh key section. https://github.com/settings/keys

30. And yup!! that set, you are done with setup your project for git+github... in 30 steps. 🎉🎉🎉

### Uploading your project to github.

Assume that, you already setup your project for git & github.

1. Open your dersired project in code assist pro.

2. From git, select status option.

3. You will see a message untracked files and then there will be a list of files. This means you have changes.

4. From git, select add all to staged. You see a message that all changes staged

5. Then again from git, select commit.

6. Enter a message (preferably `Initial Commit`) and then commit.

7. Then from git, select push.

8. Then from git, select pull. (optional but recommend).

9. You have successfully uploaded your code. see on your github repository..

10. Now you can freely do things, in your code assist pro and make new bugs (morely like accidently) but this will not affect backup. and once you again make enough bug (aka codes changes), take a rebackup 😂.

### Reuploading / Updating Project.

1. Firstly, ensure that you have no bug in your project. (optional but, recommend because github should be treat as backup and you ideally don't need bugs in back up or do you?).

2. Now from git, select pull. (not required, but recommend as you in code assist pro that abstract git. So, it always good to pull before commit in code assist unless you have terminal/termux near by)

3. Now, from git, select status option.

4. You will see a message untracked files and then there will be a list of files. This means you have changes.

5. If you see some like no changes or working tree clean. then you didn;t produce enough bugs (aka, make changes to your code).

6. Now, from git, select add all to staged. then commit the new changes, and then push as simple as that....

7. And you again good make changes asyou have backup...


## Next Step

Understand git and github more. [best-git-codeassist.md](best-git-codeassist.md)
