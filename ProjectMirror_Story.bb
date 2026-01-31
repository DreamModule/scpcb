; Project Mirror - Story System
; Маркус = охранник, не ученый. День 1 = рутина до катастрофы

Const MAX_DIALOG_OPTIONS% = 6
Const MAX_STORY_FLAGS% = 64
Const MAX_DIALOG_NODES% = 256

; ветки сюжета
Const STORY_BRANCH_NEUTRAL% = 0
Const STORY_BRANCH_REDEMPTION% = 1
Const STORY_BRANCH_CHAOS% = 2
Const STORY_BRANCH_SACRIFICE% = 3

Const KARMA_MIN% = -100
Const KARMA_MAX% = 100

Global CurrentDay% = 1
Global CurrentKarma% = 0
Global StoryBranch% = STORY_BRANCH_NEUTRAL
Global CanPlayerMove% = True
Global DialogActive% = False
Global CurrentDialogNode.DialogNode = Null

Dim StoryFlags%(MAX_STORY_FLAGS)

; флаги сюжета - индексы
Const FLAG_STEVE_MET% = 0
Const FLAG_STEVE_SAVED% = 1
Const FLAG_STEVE_DEAD% = 2
Const FLAG_HARRISON_PDA% = 3
Const FLAG_HARRISON_EYE% = 4
Const FLAG_049_CURED% = 5
Const FLAG_106_CONTAINED% = 6
Const FLAG_GUARD_SPARED% = 7
Const FLAG_SCIENTIST_HELPED% = 8
Const FLAG_MTF_CONTACTED% = 9
Const FLAG_CHAOS_ALLIED% = 10
Const FLAG_939_ENCOUNTER% = 11
Const FLAG_GATE_A_UNLOCKED% = 12
Const FLAG_GATE_B_UNLOCKED% = 13
Const FLAG_O5_CARD_OBTAINED% = 14
Const FLAG_WARHEAD_ARMED% = 15
Const FLAG_FINALE_TRIGGERED% = 16
Const FLAG_ECHO_STEVE_SEEN% = 17
Const FLAG_ECHO_GUARD_SEEN% = 18
Const FLAG_ECHO_SCIENTIST_SEEN% = 19
; Day 1 флаги
Const FLAG_COFFEE_WITH_STEVE% = 20
Const FLAG_SAW_HELICOPTERS% = 21
Const FLAG_ESCORTED_DCLASS% = 22
Const FLAG_SAW_999% = 23
Const FLAG_HEARD_173_RUMORS% = 24
Const FLAG_BREACH_STARTED% = 25

; Day 2 флаги - "Protocol & Premonition"
Const FLAG_DAY2_STARTED% = 26
Const FLAG_MET_CONVOY% = 27
Const FLAG_SAW_D9341% = 28
Const FLAG_AT_173_CHAMBER% = 29
Const FLAG_WITNESSED_PROCEDURE% = 30
Const FLAG_LIGHTS_FLICKERED% = 31
Const FLAG_PROCEDURE_COMPLETE% = 32
Const FLAG_SAW_HARRISON_TERMINAL% = 33
Const FLAG_079_INTEGRATION% = 34
Const FLAG_DAY2_COMPLETE% = 35

Global DayTransitionPending% = False
Global DayTransitionTimer# = 0.0
Global DayTransitionFade# = 0.0

Global HasHarrisonPDA% = False
Global HasHarrisonEye% = False

; протагонист - Маркус, охранник
Global PlayerName$ = "Markus"
Global PlayerRole$ = "Security Guard"
Global PlayerSecurityLevel% = 2

Type StoryState
	Field day%
	Field karma%
	Field branch%
	Field playTime#
	Field deathCount%
	Field checkpointRoom$
	Field checkpointX#
	Field checkpointY#
	Field checkpointZ#
End Type

Global GStoryState.StoryState = Null

Type DialogNode
	Field id%
	Field speakerName$
	Field text$
	Field portraitPath$
	Field portrait%
	Field voicePath$
	Field voiceChannel%
	Field autoAdvanceTime#
	Field typewriterPos%
	Field typewriterTimer#
	Field displayedText$
	Field optionCount%
	Field isTerminal%
End Type

Type DialogOption
	Field parentNode.DialogNode
	Field optionIndex%
	Field text$
	Field nextNodeID%
	Field karmaChange%
	Field flagToSet%
	Field flagValue%
	Field requiredFlag%
	Field requiredFlagValue%
	Field branchChange%
	Field visible%
End Type

Type DialogEventRecord
	Field day%
	Field roomName$
	Field roomX#, roomY#, roomZ#
	Field speakerName$
	Field soundPath$
	Field animationName$
	Field timestamp#
End Type

; триггеры для диалогов в комнатах
Type DialogTrigger
	Field roomName$
	Field dialogID%
	Field triggerRadius#
	Field oneShot%
	Field triggered%
	Field requiredDay%
	Field requiredFlag%
	Field requiredFlagValue%
End Type

Global DialogBoxTexture% = 0
Global DialogFont% = 0
Global DialogSelectedOption% = 0
Global DialogTypewriterSpeed# = 0.05

Dim DialogOptions.DialogOption(MAX_DIALOG_OPTIONS)
Dim DialogNodeCache.DialogNode(MAX_DIALOG_NODES)

; === ДЕНЬ 2: КАТСЦЕНА 173 ===

; состояния катсцены
Const SCENE_INACTIVE% = 0
Const SCENE_WAITING_PLAYER% = 1
Const SCENE_INTRO% = 2
Const SCENE_DCLASS_ENTER% = 3
Const SCENE_ANNOUNCEMENT% = 4
Const SCENE_LIGHTS_FLICKER% = 5
Const SCENE_LIGHTS_RESTORE% = 6
Const SCENE_HARRISON_VOICE% = 7
Const SCENE_DCLASS_EXIT% = 8
Const SCENE_STEVE_RADIO% = 9
Const SCENE_COMPLETE% = 10

Type ContainmentScene
	Field state%
	Field timer#
	Field phase%
	Field room173.Rooms
	Field playerPosition%
	Field lightsFlickering%
	Field flickerCount%
	Field flickerTimer#
	Field announcementPlayed%
	Field intercomChannel%
End Type

Global ActiveScene.ContainmentScene = Null

; NPC актёры для катсцены
Type SceneActor
	Field npc.NPCs
	Field role$
	Field targetX#, targetY#, targetZ#
	Field state%
	Field visible%
End Type

Global SceneSteve.SceneActor = Null
Global SceneGuard1.SceneActor = Null
Global SceneGuard2.SceneActor = Null
Global SceneDClass1.SceneActor = Null
Global SceneDClass2.SceneActor = Null
Global SceneDClass9341.SceneActor = Null

; звуки интро оригинала
Global IntroAnnouncementSFX% = 0
Global LightsFlickerSFX% = 0
Global IntercomSFX% = 0
Global AlarmSFX% = 0

; субтитры
Global SubtitleText$ = ""
Global SubtitleTimer# = 0.0
Global SubtitleSpeaker$ = ""

Function InitStorySystem()
	If GStoryState = Null Then
		GStoryState = New StoryState
		GStoryState\day = 1
		GStoryState\karma = 0
		GStoryState\branch = STORY_BRANCH_NEUTRAL
		GStoryState\playTime = 0.0
		GStoryState\deathCount = 0
		GStoryState\checkpointRoom = ""
	EndIf

	For i% = 0 To MAX_STORY_FLAGS - 1
		StoryFlags(i) = 0
	Next

	CurrentDay = GStoryState\day
	CurrentKarma = GStoryState\karma
	StoryBranch = GStoryState\branch

	DialogActive = False
	CurrentDialogNode = Null
	DialogSelectedOption = 0
	CanPlayerMove = True

	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		DialogOptions(i) = Null
	Next

	HasHarrisonPDA = False
	HasHarrisonEye = False

	; инит диалогов
	SetupDay1Dialogs()
	SetupDay1Triggers()
	SetupDay2Dialogs()
	SetupDay2Triggers()

	; звуки для катсцены
	IntroAnnouncementSFX = LoadSound("SFX\Room\Intro\Announcement.ogg")
	LightsFlickerSFX = LoadSound("SFX\General\LightFlicker.ogg")
	IntercomSFX = LoadSound("SFX\General\Intercom.ogg")
End Function

Function SetStoryFlag(flagIndex%, value% = 1)
	If flagIndex >= 0 And flagIndex < MAX_STORY_FLAGS Then
		StoryFlags(flagIndex) = value

		Select flagIndex
			Case FLAG_HARRISON_PDA
				HasHarrisonPDA = value
			Case FLAG_HARRISON_EYE
				HasHarrisonEye = value
		End Select
	EndIf
End Function

Function GetStoryFlag%(flagIndex%)
	If flagIndex >= 0 And flagIndex < MAX_STORY_FLAGS Then
		Return StoryFlags(flagIndex)
	EndIf
	Return 0
End Function

Function ToggleStoryFlag(flagIndex%)
	If flagIndex >= 0 And flagIndex < MAX_STORY_FLAGS Then
		StoryFlags(flagIndex) = 1 - StoryFlags(flagIndex)
	EndIf
End Function

Function ModifyKarma(amount%)
	CurrentKarma = CurrentKarma + amount

	If CurrentKarma < KARMA_MIN Then CurrentKarma = KARMA_MIN
	If CurrentKarma > KARMA_MAX Then CurrentKarma = KARMA_MAX

	If GStoryState <> Null Then
		GStoryState\karma = CurrentKarma
	EndIf

	UpdateStoryBranch()
End Function

Function UpdateStoryBranch()
	Local oldBranch% = StoryBranch

	If CurrentKarma >= 50 Then
		StoryBranch = STORY_BRANCH_REDEMPTION
	ElseIf CurrentKarma <= -50 Then
		StoryBranch = STORY_BRANCH_CHAOS
	ElseIf GetStoryFlag(FLAG_049_CURED) And GetStoryFlag(FLAG_STEVE_SAVED) Then
		StoryBranch = STORY_BRANCH_SACRIFICE
	Else
		StoryBranch = STORY_BRANCH_NEUTRAL
	EndIf

	If GStoryState <> Null Then
		GStoryState\branch = StoryBranch
	EndIf
End Function

Function GetKarmaLevel%()
	If CurrentKarma >= 75 Then Return 3
	If CurrentKarma >= 25 Then Return 2
	If CurrentKarma >= -25 Then Return 1
	If CurrentKarma >= -75 Then Return 0
	Return -1
End Function

Function TriggerDayTransition(newDay%)
	If newDay > 0 And newDay <= 3 And newDay > CurrentDay Then
		DayTransitionPending = True
		DayTransitionTimer = 0.0
		DayTransitionFade = 0.0

		CurrentDay = newDay
		If GStoryState <> Null Then
			GStoryState\day = newDay
		EndIf
	EndIf
End Function

Function UpdateDayTransition()
	If Not DayTransitionPending Then Return

	DayTransitionTimer = DayTransitionTimer + FPSfactor

	If DayTransitionTimer < 70.0 Then
		DayTransitionFade = DayTransitionTimer / 70.0
		CanPlayerMove = False
	ElseIf DayTransitionTimer < 210.0 Then
		DayTransitionFade = 1.0
	ElseIf DayTransitionTimer < 280.0 Then
		DayTransitionFade = 1.0 - ((DayTransitionTimer - 210.0) / 70.0)
	Else
		DayTransitionPending = False
		DayTransitionFade = 0.0
		CanPlayerMove = True
	EndIf
End Function

Function RenderDayTransition()
	If Not DayTransitionPending Then Return
	If DayTransitionFade <= 0.0 Then Return

	Color 0, 0, 0
	Local alpha% = Int(DayTransitionFade * 255.0)

	If DayTransitionFade > 0.5 Then
		Rect 0, 0, GraphicsWidth(), GraphicsHeight(), True

		If DayTransitionTimer >= 70.0 And DayTransitionTimer < 210.0 Then
			Color 200, 200, 200
			Local dayText$ = "ДЕНЬ " + CurrentDay
			Local tw% = StringWidth(dayText)
			Text (GraphicsWidth() - tw) / 2, GraphicsHeight() / 2 - 20, dayText

			Local subtitle$ = ""
			Select CurrentDay
				Case 1
					subtitle = "РУТИНА"
				Case 2
					subtitle = "ПРОТОКОЛ"
				Case 3
					subtitle = "РАСПЛАТА"
			End Select

			Color 150, 150, 150
			tw = StringWidth(subtitle)
			Text (GraphicsWidth() - tw) / 2, GraphicsHeight() / 2 + 10, subtitle
		EndIf
	EndIf
End Function

Function CreateDialogNode.DialogNode(id%, speakerName$, text$, portraitPath$ = "", voicePath$ = "")
	Local node.DialogNode = New DialogNode

	node\id = id
	node\speakerName = speakerName
	node\text = text
	node\portraitPath = portraitPath
	node\voicePath = voicePath
	node\voiceChannel = 0
	node\autoAdvanceTime = 0.0
	node\typewriterPos = 0
	node\typewriterTimer = 0.0
	node\displayedText = ""
	node\optionCount = 0
	node\isTerminal = True

	If portraitPath <> "" Then
		node\portrait = LoadImage(portraitPath)
		If node\portrait <> 0 Then
			MaskImage node\portrait, 255, 0, 255
		EndIf
	EndIf

	If id >= 0 And id < MAX_DIALOG_NODES Then
		DialogNodeCache(id) = node
	EndIf

	Return node
End Function

Function AddDialogOption.DialogOption(node.DialogNode, text$, nextNodeID%, karmaChange% = 0, flagToSet% = -1, flagValue% = 1)
	If node = Null Then Return Null
	If node\optionCount >= MAX_DIALOG_OPTIONS Then Return Null

	Local opt.DialogOption = New DialogOption

	opt\parentNode = node
	opt\optionIndex = node\optionCount
	opt\text = text
	opt\nextNodeID = nextNodeID
	opt\karmaChange = karmaChange
	opt\flagToSet = flagToSet
	opt\flagValue = flagValue
	opt\requiredFlag = -1
	opt\requiredFlagValue = 0
	opt\branchChange = -1
	opt\visible = True

	DialogOptions(node\optionCount) = opt

	node\optionCount = node\optionCount + 1
	node\isTerminal = False

	Return opt
End Function

Function SetOptionRequirement(opt.DialogOption, flagIndex%, requiredValue% = 1)
	If opt <> Null Then
		opt\requiredFlag = flagIndex
		opt\requiredFlagValue = requiredValue
	EndIf
End Function

Function SetOptionBranchChange(opt.DialogOption, newBranch%)
	If opt <> Null Then
		opt\branchChange = newBranch
	EndIf
End Function

Function StartDialog(nodeID%)
	If nodeID < 0 Or nodeID >= MAX_DIALOG_NODES Then Return

	Local node.DialogNode = DialogNodeCache(nodeID)
	If node = Null Then Return

	CurrentDialogNode = node
	DialogActive = True
	CanPlayerMove = False
	DialogSelectedOption = 0

	node\typewriterPos = 0
	node\typewriterTimer = 0.0
	node\displayedText = ""

	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		DialogOptions(i) = Null
	Next

	Local optIndex% = 0
	For opt.DialogOption = Each DialogOption
		If opt\parentNode = node Then
			opt\visible = True
			If opt\requiredFlag >= 0 Then
				If GetStoryFlag(opt\requiredFlag) <> opt\requiredFlagValue Then
					opt\visible = False
				EndIf
			EndIf

			If opt\visible Then
				DialogOptions(optIndex) = opt
				optIndex = optIndex + 1
			EndIf
		EndIf
	Next

	If node\voicePath <> "" Then
		Local snd% = LoadSound(node\voicePath)
		If snd <> 0 Then
			node\voiceChannel = PlaySound(snd)
		EndIf
	EndIf
End Function

Function UpdateDialog()
	If Not DialogActive Then Return
	If CurrentDialogNode = Null Then
		EndDialog()
		Return
	EndIf

	Local node.DialogNode = CurrentDialogNode

	node\typewriterTimer = node\typewriterTimer + FPSfactor * DialogTypewriterSpeed
	If node\typewriterTimer >= 1.0 Then
		node\typewriterTimer = node\typewriterTimer - 1.0
		If node\typewriterPos < Len(node\text) Then
			node\typewriterPos = node\typewriterPos + 1
			node\displayedText = Left$(node\text, node\typewriterPos)
		EndIf
	EndIf

	If KeyHit(200) Then
		DialogSelectedOption = DialogSelectedOption - 1
		If DialogSelectedOption < 0 Then
			DialogSelectedOption = CountVisibleOptions() - 1
		EndIf
		While DialogOptions(DialogSelectedOption) = Null And DialogSelectedOption > 0
			DialogSelectedOption = DialogSelectedOption - 1
		Wend
	EndIf

	If KeyHit(208) Then
		DialogSelectedOption = DialogSelectedOption + 1
		If DialogSelectedOption >= CountVisibleOptions() Then
			DialogSelectedOption = 0
		EndIf
		While DialogOptions(DialogSelectedOption) = Null And DialogSelectedOption < MAX_DIALOG_OPTIONS - 1
			DialogSelectedOption = DialogSelectedOption + 1
		Wend
	EndIf

	If KeyHit(57) Then
		If node\typewriterPos < Len(node\text) Then
			node\typewriterPos = Len(node\text)
			node\displayedText = node\text
		EndIf
	EndIf

	If KeyHit(28) Or KeyHit(18) Then
		If node\typewriterPos >= Len(node\text) Then
			SelectDialogOption()
		Else
			node\typewriterPos = Len(node\text)
			node\displayedText = node\text
		EndIf
	EndIf

	If node\isTerminal And node\autoAdvanceTime > 0.0 Then
		If node\typewriterPos >= Len(node\text) Then
			node\autoAdvanceTime = node\autoAdvanceTime - FPSfactor
			If node\autoAdvanceTime <= 0.0 Then
				EndDialog()
			EndIf
		EndIf
	EndIf
End Function

Function CountVisibleOptions%()
	Local count% = 0
	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		If DialogOptions(i) <> Null Then
			count = count + 1
		EndIf
	Next

	If count = 0 Then count = 1
	Return count
End Function

Function SelectDialogOption()
	Local opt.DialogOption = DialogOptions(DialogSelectedOption)

	If opt <> Null Then
		If opt\karmaChange <> 0 Then
			ModifyKarma(opt\karmaChange)
		EndIf

		If opt\flagToSet >= 0 Then
			SetStoryFlag(opt\flagToSet, opt\flagValue)
		EndIf

		If opt\branchChange >= 0 Then
			StoryBranch = opt\branchChange
			If GStoryState <> Null Then
				GStoryState\branch = StoryBranch
			EndIf
		EndIf

		If opt\nextNodeID >= 0 Then
			StartDialog(opt\nextNodeID)
		Else
			EndDialog()
		EndIf
	Else
		EndDialog()
	EndIf
End Function

Function EndDialog()
	If CurrentDialogNode <> Null Then
		If CurrentDialogNode\voiceChannel <> 0 Then
			StopChannel CurrentDialogNode\voiceChannel
		EndIf
	EndIf

	DialogActive = False
	CurrentDialogNode = Null
	CanPlayerMove = True
	DialogSelectedOption = 0

	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		DialogOptions(i) = Null
	Next
End Function

Function RenderDialog()
	If Not DialogActive Then Return
	If CurrentDialogNode = Null Then Return

	Local node.DialogNode = CurrentDialogNode
	Local gw% = GraphicsWidth()
	Local gh% = GraphicsHeight()

	Local boxWidth% = gw - 100
	Local boxHeight% = 200
	Local boxX% = 50
	Local boxY% = gh - boxHeight - 30

	Color 0, 0, 0
	Rect boxX, boxY, boxWidth, boxHeight, True

	Color 100, 100, 100
	Rect boxX, boxY, boxWidth, boxHeight, False
	Rect boxX + 1, boxY + 1, boxWidth - 2, boxHeight - 2, False

	Local textStartX% = boxX + 20
	If node\portrait <> 0 Then
		DrawImage node\portrait, boxX + 10, boxY + 10
		textStartX = boxX + 110
	EndIf

	Color 200, 180, 100
	Text textStartX, boxY + 15, node\speakerName

	Color 220, 220, 220
	Local textY% = boxY + 40
	Local maxWidth% = boxWidth - (textStartX - boxX) - 20

	DrawWrappedText(node\displayedText, textStartX, textY, maxWidth)

	If node\typewriterPos >= Len(node\text) Then
		Local optY% = boxY + boxHeight - 80
		Local optIndex% = 0

		For i% = 0 To MAX_DIALOG_OPTIONS - 1
			Local opt.DialogOption = DialogOptions(i)
			If opt <> Null Then
				If optIndex = DialogSelectedOption Then
					Color 255, 200, 50
					Text textStartX - 15, optY, ">"
				Else
					Color 180, 180, 180
				EndIf

				Text textStartX, optY, (optIndex + 1) + ". " + opt\text

				If opt\karmaChange > 0 Then
					Color 100, 200, 100
					Text textStartX + StringWidth((optIndex + 1) + ". " + opt\text) + 10, optY, "[+" + opt\karmaChange + "]"
				ElseIf opt\karmaChange < 0 Then
					Color 200, 100, 100
					Text textStartX + StringWidth((optIndex + 1) + ". " + opt\text) + 10, optY, "[" + opt\karmaChange + "]"
				EndIf

				optY = optY + 18
				optIndex = optIndex + 1
			EndIf
		Next

		If optIndex = 0 Then
			Color 150, 150, 150
			Text textStartX, optY, "[ENTER - продолжить]"
		EndIf
	Else
		Color 100, 100, 100
		Text textStartX, boxY + boxHeight - 25, "[SPACE - пропустить]"
	EndIf
End Function

Function DrawWrappedText(txt$, x%, y%, maxWidth%)
	Local words$ = txt
	Local line$ = ""
	Local lineY% = y
	Local spacePos%
	Local word$

	While Len(words) > 0
		spacePos = Instr(words, " ")
		If spacePos = 0 Then
			word = words
			words = ""
		Else
			word = Left$(words, spacePos - 1)
			words = Mid$(words, spacePos + 1)
		EndIf

		If StringWidth(line + word) > maxWidth Then
			Text x, lineY, line
			lineY = lineY + 16
			line = word + " "
		Else
			line = line + word + " "
		EndIf
	Wend

	If Len(line) > 0 Then
		Text x, lineY, line
	EndIf
End Function

Function RecordDialogEvent(room.Rooms, speakerName$, soundPath$, animName$ = "")
	If room = Null Then Return

	Local rec.DialogEventRecord = New DialogEventRecord
	rec\day = CurrentDay
	rec\roomName = room\RoomTemplate\Name
	rec\roomX = EntityX(room\obj)
	rec\roomY = EntityY(room\obj)
	rec\roomZ = EntityZ(room\obj)
	rec\speakerName = speakerName
	rec\soundPath = soundPath
	rec\animationName = animName
	rec\timestamp = MilliSecs()
End Function

Function GetDialogEventsForRoom.DialogEventRecord(room.Rooms, targetDay%)
	If room = Null Then Return Null

	For rec.DialogEventRecord = Each DialogEventRecord
		If rec\day = targetDay Then
			If rec\roomName = room\RoomTemplate\Name Then
				Return rec
			EndIf
		EndIf
	Next

	Return Null
End Function

; === ДЕНЬ 1: ДИАЛОГИ ===
; Маркус - охранник. Утро, рутина, потом все идет к черту

Function SetupDay1Dialogs()
	Local node.DialogNode
	Local opt.DialogOption

	; --- УТРО: КОФЕ СО СТИВОМ ---
	; Стив тоже охранник, напарник Маркуса

	node = CreateDialogNode(1, "Steve", "Yo, Markus. Opjat' vertushki s utra spat' ne dayut. Govoryat, bol'shuyu shishku vezyut dlya testa.", "", "")
	opt = AddDialogOption(node, "Rabota est' rabota, Steve.", 2, 0, FLAG_COFFEE_WITH_STEVE, 1)
	opt = AddDialogOption(node, "Lish' by platili vovremia.", 3, -2, FLAG_COFFEE_WITH_STEVE, 1)
	opt = AddDialogOption(node, "Poidem glyanem, poka nachal'stva net?", 4, 3, FLAG_COFFEE_WITH_STEVE, 1)

	node = CreateDialogNode(2, "Steve", "Aga, filosof. Ladno, dopivai kofe - cherez chas konvoi. D-klassy sami sebya ne dovedut.", "", "")
	AddDialogOption(node, "[Kivaesh']", -1, 0, -1, 0)

	node = CreateDialogNode(3, "Steve", "*khekhaet* Tsenik ty, Markus. No ya tebya ponimau. Eta kontora... stranno tut vse.", "", "")
	opt = AddDialogOption(node, "Strannee, chem ty dumaesh'.", 5, 0, -1, 0)
	opt = AddDialogOption(node, "Luchshe ne znat' podrobnostei.", -1, 0, -1, 0)

	node = CreateDialogNode(4, "Steve", "O, azart! Ladno, tol'ko bystro. Esli kapitan zametit - ya tebya ne znayu.", "", "")
	AddDialogOption(node, "Dogovorilis'.", 6, 0, FLAG_SAW_HELICOPTERS, 1)

	node = CreateDialogNode(5, "Steve", "...ty chto-to znaesh', da? Pro eti... SCP-ob'ekty?", "", "")
	opt = AddDialogOption(node, "Men'she znaesh' - krepche spish'.", -1, -1, -1, 0)
	opt = AddDialogOption(node, "Kak-nibud' rasskazhu. Ne seichas.", -1, 2, -1, 0)

	; --- ВЕРТОЛЁТЫ НА GATE A ---

	node = CreateDialogNode(6, "Steve", "*smotrit na vertolet* Nichego sebe gruzovik. Chto eto voobshche? Kakaya-to ustanovka?", "", "")
	opt = AddDialogOption(node, "Pohoze na medicinskoe oborudovanie.", 7, 0, -1, 0)
	opt = AddDialogOption(node, "Ne nashi problemy.", -1, -1, -1, 0)

	node = CreateDialogNode(7, "Steve", "Igrushki dlya yaitsegolovyh... *vzdyhaet* Ladno, poshli obratno. Konvoi cherez 20 minut.", "", "")
	AddDialogOption(node, "[Vozvrashchaetes']", -1, 0, -1, 0)

	; --- КОНВОЙ К SCP-999 ---

	node = CreateDialogNode(10, "Steve", "Konvoi 'Miloserdie'. Tri D-klassa. Vedyom v Light Containment, k 999-mu.", "", "")
	AddDialogOption(node, "999? Eto tot, kotoryi...", 11, 0, -1, 0)
	AddDialogOption(node, "Ponyal. Poidem.", 12, 0, -1, 0)

	node = CreateDialogNode(11, "Steve", "Aga, oranzhevyy blob. Bezobidnyi. Dazhe D-klassam razreshayut... obshchat'sya s nim. Terapiya, chto li.", "", "")
	opt = AddDialogOption(node, "Horosho, chto est' chto-to nestrashnoye tut.", 12, 2, -1, 0)
	opt = AddDialogOption(node, "Vsyo ravno strannaya kontora.", 12, 0, -1, 0)

	node = CreateDialogNode(12, "Steve", "*D-klassam* Na nogi, gospoda. Ekskursiya nachinayetsya.", "", "")
	AddDialogOption(node, "[Nachinayete konvoi]", -1, 0, FLAG_ESCORTED_DCLASS, 1)

	; --- У SCP-999 ---

	node = CreateDialogNode(20, "Steve", "*smotrit kak 999 obnimayet D-klassa* Smotri, kak oni raduyutsya. Mozhet, my tut ne tol'ko monstrov derzhim, a?", "", "")
	opt = AddDialogOption(node, "Mozhet i tak. Redkii svetlyi moment.", 21, 3, FLAG_SAW_999, 1)
	opt = AddDialogOption(node, "Ne rasslablyaisya. Eto vsyo eshcho SCP.", 22, 0, FLAG_SAW_999, 1)
	opt = AddDialogOption(node, "*molcha nabludaesh'*", -1, 0, FLAG_SAW_999, 1)

	node = CreateDialogNode(21, "Steve", "Da... *pauza* Znaesh', inogda dumayu - zachem eto vsyo? A potom vizhu takoye, i... nu, ty ponyal.", "", "")
	AddDialogOption(node, "Ponyal.", -1, 1, -1, 0)

	node = CreateDialogNode(22, "Steve", "*vzdyhaet* Ty prav, konechno. No dazhe nam inogda nuzhno... chto-to horosheye videt'.", "", "")
	AddDialogOption(node, "[Kivaesh']", -1, 0, -1, 0)

	; --- ВЕЧЕР: СТОЛОВАЯ ---

	node = CreateDialogNode(30, "Guard Johnson", "*shepchet* Slyshali pro 173-go? Govoryat, zavtra 'chistka'. Opjat' kogo-to zatashchili vnutr'...", "", "")
	opt = AddDialogOption(node, "Ne nashi problemy.", -1, -1, -1, 0)
	opt = AddDialogOption(node, "Bednye ublYudki.", 31, 1, -1, 0)
	opt = AddDialogOption(node, "Ty eto ne slyshal, i ya tozhe.", -1, 0, FLAG_HEARD_173_RUMORS, 1)

	node = CreateDialogNode(31, "Guard Johnson", "Da uzh... *ogladyvaetsya* Luchshe molchi ob etom. Steny tut imeyut ushi.", "", "")
	AddDialogOption(node, "[Kivaesh' i uhodish']", -1, 0, FLAG_HEARD_173_RUMORS, 1)

	; --- НОЧЬ: ТРЕВОГА ---
	; etot dialog triggernetsya kogda nachnyotsya proryv

	node = CreateDialogNode(50, "[TREVOGA]", "VNIMANIE. MASSOVYI PRORYV SODERZHANIYA. VES' PERSONAL - SLEDOVAT' PROTOKOLU 'OMEGA-7'.", "", "")
	node\autoAdvanceTime = 140.0

	node = CreateDialogNode(51, "Steve", "*po racii* MARKUS! Ty zhiv?! 173-yi vyrvalsya! Ya u Gate B - dui syuda!", "", "")
	opt = AddDialogOption(node, "Derzhi'sya, Steve! Idu!", -1, 2, FLAG_BREACH_STARTED, 1)
	opt = AddDialogOption(node, "Steve, uhoadi bez menya! Ya poprobuyu nayti vyhod!", -1, 0, FLAG_BREACH_STARTED, 1)
End Function

Function SetupDay1Triggers()
	Local trig.DialogTrigger

	; kafeteriy - razgovor so Stivom
	trig = New DialogTrigger
	trig\roomName = "room2cafeteria"
	trig\dialogID = 1
	trig\triggerRadius = 3.0
	trig\oneShot = True
	trig\triggered = False
	trig\requiredDay = 1
	trig\requiredFlag = -1

	; room2toilets - slukhi pro 173
	trig = New DialogTrigger
	trig\roomName = "room2toilets"
	trig\dialogID = 30
	trig\triggerRadius = 2.5
	trig\oneShot = True
	trig\triggered = False
	trig\requiredDay = 1
	trig\requiredFlag = FLAG_ESCORTED_DCLASS
	trig\requiredFlagValue = 1
End Function

Function CheckDialogTriggers(room.Rooms)
	If room = Null Then Return
	If DialogActive Then Return

	Local roomName$ = ""
	If room\RoomTemplate <> Null Then
		roomName = room\RoomTemplate\Name
	EndIf

	For trig.DialogTrigger = Each DialogTrigger
		If trig\triggered And trig\oneShot Then Continue
		If trig\roomName <> roomName Then Continue
		If trig\requiredDay > 0 And trig\requiredDay <> CurrentDay Then Continue
		If trig\requiredFlag >= 0 Then
			If GetStoryFlag(trig\requiredFlag) <> trig\requiredFlagValue Then Continue
		EndIf

		; trigger!
		trig\triggered = True
		StartDialog(trig\dialogID)
		Return
	Next
End Function

Function TriggerBreachSequence()
	; nachalo proryva - den' 2
	SetStoryFlag(FLAG_BREACH_STARTED, 1)
	StartDialog(50)
End Function

Function SaveStoryState(file%)
	If GStoryState = Null Then Return

	WriteInt file, CurrentDay
	WriteInt file, CurrentKarma
	WriteInt file, StoryBranch
	WriteFloat file, GStoryState\playTime
	WriteInt file, GStoryState\deathCount
	WriteLine file, GStoryState\checkpointRoom
	WriteFloat file, GStoryState\checkpointX
	WriteFloat file, GStoryState\checkpointY
	WriteFloat file, GStoryState\checkpointZ

	For i% = 0 To MAX_STORY_FLAGS - 1
		WriteInt file, StoryFlags(i)
	Next

	Local recCount% = 0
	For rec.DialogEventRecord = Each DialogEventRecord
		recCount = recCount + 1
	Next
	WriteInt file, recCount

	For rec.DialogEventRecord = Each DialogEventRecord
		WriteInt file, rec\day
		WriteLine file, rec\roomName
		WriteFloat file, rec\roomX
		WriteFloat file, rec\roomY
		WriteFloat file, rec\roomZ
		WriteLine file, rec\speakerName
		WriteLine file, rec\soundPath
		WriteLine file, rec\animationName
		WriteFloat file, rec\timestamp
	Next

	; sohranyaem triggery
	Local trigCount% = 0
	For trig.DialogTrigger = Each DialogTrigger
		trigCount = trigCount + 1
	Next
	WriteInt file, trigCount

	For trig.DialogTrigger = Each DialogTrigger
		WriteLine file, trig\roomName
		WriteInt file, trig\dialogID
		WriteInt file, trig\triggered
	Next
End Function

Function LoadStoryState(file%)
	If GStoryState = Null Then
		GStoryState = New StoryState
	EndIf

	CurrentDay = ReadInt(file)
	CurrentKarma = ReadInt(file)
	StoryBranch = ReadInt(file)
	GStoryState\playTime = ReadFloat(file)
	GStoryState\deathCount = ReadInt(file)
	GStoryState\checkpointRoom = ReadLine(file)
	GStoryState\checkpointX = ReadFloat(file)
	GStoryState\checkpointY = ReadFloat(file)
	GStoryState\checkpointZ = ReadFloat(file)

	GStoryState\day = CurrentDay
	GStoryState\karma = CurrentKarma
	GStoryState\branch = StoryBranch

	For i% = 0 To MAX_STORY_FLAGS - 1
		StoryFlags(i) = ReadInt(file)
	Next

	HasHarrisonPDA = StoryFlags(FLAG_HARRISON_PDA)
	HasHarrisonEye = StoryFlags(FLAG_HARRISON_EYE)

	Local recCount% = ReadInt(file)
	For i% = 0 To recCount - 1
		Local rec.DialogEventRecord = New DialogEventRecord
		rec\day = ReadInt(file)
		rec\roomName = ReadLine(file)
		rec\roomX = ReadFloat(file)
		rec\roomY = ReadFloat(file)
		rec\roomZ = ReadFloat(file)
		rec\speakerName = ReadLine(file)
		rec\soundPath = ReadLine(file)
		rec\animationName = ReadLine(file)
		rec\timestamp = ReadFloat(file)
	Next

	Local trigCount% = ReadInt(file)
	For i% = 0 To trigCount - 1
		Local roomN$ = ReadLine(file)
		Local dID% = ReadInt(file)
		Local trig% = ReadInt(file)

		For tr.DialogTrigger = Each DialogTrigger
			If tr\roomName = roomN And tr\dialogID = dID Then
				tr\triggered = trig
				Exit
			EndIf
		Next
	Next
End Function

Function CleanupStorySystem()
	For node.DialogNode = Each DialogNode
		If node\portrait <> 0 Then
			FreeImage node\portrait
		EndIf
		Delete node
	Next

	For opt.DialogOption = Each DialogOption
		Delete opt
	Next

	For rec.DialogEventRecord = Each DialogEventRecord
		Delete rec
	Next

	For trig.DialogTrigger = Each DialogTrigger
		Delete trig
	Next

	For i% = 0 To MAX_DIALOG_NODES - 1
		DialogNodeCache(i) = Null
	Next

	If GStoryState <> Null Then
		Delete GStoryState
		GStoryState = Null
	EndIf
End Function

Function DebugStoryState()
	Color 255, 255, 255
	Text 10, 10, "=== MIRROR DEBUG ==="
	Text 10, 30, "Day: " + CurrentDay
	Text 10, 45, "Karma: " + CurrentKarma + " (lvl: " + GetKarmaLevel() + ")"
	Text 10, 60, "Branch: " + GetBranchName(StoryBranch)
	Text 10, 75, "Dialog: " + DialogActive
	Text 10, 90, "CanMove: " + CanPlayerMove

	Local flagY% = 110
	Text 10, flagY, "-- Flags --"
	flagY = flagY + 15

	If GetStoryFlag(FLAG_STEVE_MET) Then Text 10, flagY, "Steve Met" : flagY = flagY + 12
	If GetStoryFlag(FLAG_STEVE_SAVED) Then Text 10, flagY, "Steve Saved" : flagY = flagY + 12
	If GetStoryFlag(FLAG_STEVE_DEAD) Then Text 10, flagY, "Steve Dead" : flagY = flagY + 12
	If GetStoryFlag(FLAG_HARRISON_PDA) Then Text 10, flagY, "Harrison PDA" : flagY = flagY + 12
	If GetStoryFlag(FLAG_HARRISON_EYE) Then Text 10, flagY, "Harrison Eye" : flagY = flagY + 12
	If GetStoryFlag(FLAG_COFFEE_WITH_STEVE) Then Text 10, flagY, "Coffee w/ Steve" : flagY = flagY + 12
	If GetStoryFlag(FLAG_SAW_999) Then Text 10, flagY, "Saw 999" : flagY = flagY + 12
	If GetStoryFlag(FLAG_BREACH_STARTED) Then Text 10, flagY, "BREACH!" : flagY = flagY + 12
	; Day 2 flags
	If GetStoryFlag(FLAG_DAY2_STARTED) Then Text 10, flagY, "Day2 Started" : flagY = flagY + 12
	If GetStoryFlag(FLAG_SAW_D9341) Then Text 10, flagY, "Saw D-9341" : flagY = flagY + 12
	If GetStoryFlag(FLAG_WITNESSED_PROCEDURE) Then Text 10, flagY, "Witnessed 173" : flagY = flagY + 12
	If GetStoryFlag(FLAG_079_INTEGRATION) Then Text 10, flagY, "079 Integration" : flagY = flagY + 12
End Function

Function GetBranchName$(branch%)
	Select branch
		Case STORY_BRANCH_NEUTRAL
			Return "NEUTRAL"
		Case STORY_BRANCH_REDEMPTION
			Return "REDEMPTION"
		Case STORY_BRANCH_CHAOS
			Return "CHAOS"
		Case STORY_BRANCH_SACRIFICE
			Return "SACRIFICE"
	End Select
	Return "UNKNOWN"
End Function

; ============================================================================
; ДЕНЬ 2: "PROTOCOL & PREMONITION"
; Маркус ведёт D-класса к 173. Твист - процедура проходит "нормально"
; ============================================================================

Function SetupDay2Dialogs()
	Local node.DialogNode
	Local opt.DialogOption

	; --- УТРО: БРИФИНГ ---
	; ID 100-109

	node = CreateDialogNode(100, "[КПК]", "ZADANIE: Soprovodit' ob'ekty klassa D k kamere soderzhaniya SCP-173. Yavit'sya v checkpoint LCZ-A.", "", "")
	node\autoAdvanceTime = 210.0

	; --- ВСТРЕЧА С КОНВОЕМ ---
	; ID 110-119

	node = CreateDialogNode(110, "Steve", "Markus! Ty zamykayushchii. Sledi, chtoby eti krysy ne dergalis'.", "", "")
	AddDialogOption(node, "Ponyal.", 111, 0, FLAG_MET_CONVOY, 1)
	AddDialogOption(node, "Skolko ih?", 112, 0, FLAG_MET_CONVOY, 1)

	node = CreateDialogNode(111, "Steve", "Osobenno etot, 9341-i. Mutnyy tip. Smotrit tak, budto znaet chto-to.", "", "")
	opt = AddDialogOption(node, "*smotrish' na 9341*", 113, 0, FLAG_SAW_D9341, 1)
	opt = AddDialogOption(node, "Vse oni odinakovy.", -1, -2, FLAG_SAW_D9341, 1)

	node = CreateDialogNode(112, "Steve", "Troe. Standart dlya chistki 173-go. Dvoe nashi, dvoe s drugoy smeny.", "", "")
	AddDialogOption(node, "Kto eshche v gruppe?", 114, 0, -1, 0)
	AddDialogOption(node, "Poidyom.", 111, 0, -1, 0)

	node = CreateDialogNode(113, "D-9341", "*molcha smotrit na tebya, potom otvodyat vzglyad*", "", "")
	node\autoAdvanceTime = 105.0

	node = CreateDialogNode(114, "Steve", "Dzhonson i Gomez. I doktor Franklin na nablyudenii. Poidyom, nam pora.", "", "")
	AddDialogOption(node, "[Sleduete za grupppoi]", -1, 0, -1, 0)

	; --- У КАМЕРЫ 173 ---
	; ID 120-129

	node = CreateDialogNode(120, "Steve", "Zanyat' pozitsii. Markus, ty u dveri. Yesli chto - strelyai bez preduprezhdeniya.", "", "")
	opt = AddDialogOption(node, "Ponyal.", 121, 0, FLAG_AT_173_CHAMBER, 1)
	opt = AddDialogOption(node, "Eto pravda neobhodimo?", 122, 2, FLAG_AT_173_CHAMBER, 1)

	node = CreateDialogNode(121, "Steve", "Franklin, nachinai. D-klassy - vnutr'.", "", "")
	node\autoAdvanceTime = 140.0

	node = CreateDialogNode(122, "Steve", "*vzdyhaet* Etot ob'ekt... On ubil uzhe mnogo lyudei. Ne veri milym rozhitsam. Vnutr'.", "", "")
	node\autoAdvanceTime = 140.0

	; --- АНОНС (КАК В ОРИГИНАЛЕ) ---
	; ID 130-139

	node = CreateDialogNode(130, "[INTERKOM]", "Attention all Class-D personnel. Please enter the containment chamber.", "", "")
	node\autoAdvanceTime = 175.0

	node = CreateDialogNode(131, "[INTERKOM]", "SCP-173 containment chamber cleaning will begin shortly. Please maintain direct eye contact with SCP-173.", "", "")
	node\autoAdvanceTime = 210.0

	; --- ТВИСТ: СВЕТ МИГАЕТ, НО ВСЁ ОК ---
	; ID 140-149

	node = CreateDialogNode(140, "[...]", "*svet migaet... slyshny strelyayushchie iskry... tishina...*", "", "")
	node\autoAdvanceTime = 140.0

	node = CreateDialogNode(141, "[...]", "*svet vklyuchayetsya obratno*", "", "")
	node\autoAdvanceTime = 70.0

	node = CreateDialogNode(142, "[INTERKOM - Harrison]", "Pokazateli v norme. Vyvodite sub'ektov. Otlichnaya rabota.", "", "")
	node\autoAdvanceTime = 140.0

	node = CreateDialogNode(143, "Steve", "*po racii* Prinyato. Proneslo, parni. Uhodim.", "", "")
	AddDialogOption(node, "[Vykhodite iz zony]", 144, 0, FLAG_PROCEDURE_COMPLETE, 1)

	node = CreateDialogNode(144, "Steve", "Markus, provodi D-klassov obratno. Ya otchitayus' Franklinu.", "", "")
	opt = AddDialogOption(node, "Sdelayem.", -1, 0, -1, 0)
	opt = AddDialogOption(node, "Mne pokazalos', ili svet...", 145, 1, -1, 0)

	node = CreateDialogNode(145, "Steve", "*pauza* ...da, migalo. Znaesh', eta kamera... Inogda proiskhodyat strannyye veshchi. No segodnya - vsyo chistо. Poydyom.", "", "")
	AddDialogOption(node, "[Kivaesh']", -1, 0, FLAG_LIGHTS_FLICKERED, 1)

	; --- ФИНАЛ ДНЯ 2: ТЕРМИНАЛ ХАРРИСОНА ---
	; ID 150-159

	node = CreateDialogNode(150, "[TERMINAL]", "SISTEMA 079 INTEGRIROVANA. ZAPUSK ALGORITMA NAZNACHEN NA 06:00 ZAVTRASHNEGO DNYA. - DR. HARRISON", "", "")
	node\autoAdvanceTime = 245.0

	node = CreateDialogNode(151, "[TERMINAL]", "PRIMECHANIE: 'Zerkalo' gotovo. Oni ne poimut, poka ne budet slishkom pozdno.", "", "")
	node\autoAdvanceTime = 175.0

	node = CreateDialogNode(152, "[...]", "*ekran gasnet*", "", "")
	node\autoAdvanceTime = 70.0

	; --- ПЕРЕХОД К ДНЮ 3 ---
	; ID 160

	node = CreateDialogNode(160, "Steve", "*po racii, ustalyi golos* Markus, smena okonchenya. Uvidimsya zavtra. Khorosho, chto segonya vsyo proshlo gladko, da?", "", "")
	opt = AddDialogOption(node, "Da... gladko.", -1, 0, FLAG_DAY2_COMPLETE, 1)
	opt = AddDialogOption(node, "U menya plohoye predchuvstvie.", -1, 3, FLAG_DAY2_COMPLETE, 1)
End Function

Function SetupDay2Triggers()
	Local trig.DialogTrigger

	; checkpoint - vstrecha s konvoem
	trig = New DialogTrigger
	trig\roomName = "checkpoint1"
	trig\dialogID = 110
	trig\triggerRadius = 4.0
	trig\oneShot = True
	trig\triggered = False
	trig\requiredDay = 2
	trig\requiredFlag = FLAG_DAY2_STARTED
	trig\requiredFlagValue = 1

	; komnata 173 - nachalo sceny
	trig = New DialogTrigger
	trig\roomName = "173"
	trig\dialogID = 120
	trig\triggerRadius = 5.0
	trig\oneShot = True
	trig\triggered = False
	trig\requiredDay = 2
	trig\requiredFlag = FLAG_MET_CONVOY
	trig\requiredFlagValue = 1

	; ofisnaya zona - terminal Harrisona
	trig = New DialogTrigger
	trig\roomName = "room2offices"
	trig\dialogID = 150
	trig\triggerRadius = 2.0
	trig\oneShot = True
	trig\triggered = False
	trig\requiredDay = 2
	trig\requiredFlag = FLAG_PROCEDURE_COMPLETE
	trig\requiredFlagValue = 1
End Function

; --- СТАРТ ДНЯ 2 ---

Function StartDay2Intro()
	If CurrentDay <> 2 Then Return
	If GetStoryFlag(FLAG_DAY2_STARTED) Then Return

	SetStoryFlag(FLAG_DAY2_STARTED, 1)

	; zadanie na KPK
	StartDialog(100)

	; spawn v checkpoint zone
	SpawnPlayerDay2()

	; spawn actorov (budut rasstavleny pozje po triggeru)
	DebugLog "Day 2 initialized"
End Function

Function SpawnPlayerDay2()
	Local spawnRoom.Rooms = Null

	For r.Rooms = Each Rooms
		If r\RoomTemplate <> Null Then
			If r\RoomTemplate\Name = "checkpoint1" Then
				spawnRoom = r
				Exit
			EndIf
		EndIf
	Next

	If spawnRoom = Null Then
		For r.Rooms = Each Rooms
			If r\RoomTemplate <> Null Then
				If Instr(r\RoomTemplate\Name, "checkpoint") > 0 Then
					spawnRoom = r
					Exit
				EndIf
			EndIf
		Next
	EndIf

	If spawnRoom <> Null Then
		Local spawnX# = EntityX(spawnRoom\obj)
		Local spawnY# = 0.5
		Local spawnZ# = EntityZ(spawnRoom\obj) + 2.0

		PositionEntity Collider, spawnX, spawnY, spawnZ
		ResetEntity Collider
		PlayerRoom = spawnRoom

		DebugLog "Day 2 spawn at checkpoint"
	EndIf
End Function

; --- РАССТАНОВКА АКТЁРОВ ---

Function SpawnDay2Actors(room.Rooms)
	If room = Null Then Return

	Local baseX# = EntityX(room\obj)
	Local baseY# = 0.5
	Local baseZ# = EntityZ(room\obj)

	; Steve
	Local steveNPC.NPCs = CreateNPC(NPCtypeGuard, baseX + 1.0, baseY, baseZ - 2.0)
	If steveNPC <> Null Then
		SceneSteve = New SceneActor
		SceneSteve\npc = steveNPC
		SceneSteve\role = "Steve"
		SceneSteve\state = 0
		SceneSteve\visible = True
	EndIf

	; Guard 1
	Local guard1NPC.NPCs = CreateNPC(NPCtypeGuard, baseX - 1.5, baseY, baseZ - 2.0)
	If guard1NPC <> Null Then
		SceneGuard1 = New SceneActor
		SceneGuard1\npc = guard1NPC
		SceneGuard1\role = "Johnson"
		SceneGuard1\state = 0
		SceneGuard1\visible = True
	EndIf

	; Guard 2
	Local guard2NPC.NPCs = CreateNPC(NPCtypeGuard, baseX + 2.5, baseY, baseZ - 2.0)
	If guard2NPC <> Null Then
		SceneGuard2 = New SceneActor
		SceneGuard2\npc = guard2NPC
		SceneGuard2\role = "Gomez"
		SceneGuard2\state = 0
		SceneGuard2\visible = True
	EndIf

	; D-Class 1
	Local d1NPC.NPCs = CreateNPC(NPCtypeDClass, baseX - 0.5, baseY, baseZ)
	If d1NPC <> Null Then
		SceneDClass1 = New SceneActor
		SceneDClass1\npc = d1NPC
		SceneDClass1\role = "D-8432"
		SceneDClass1\state = 0
		SceneDClass1\visible = True
	EndIf

	; D-Class 2
	Local d2NPC.NPCs = CreateNPC(NPCtypeDClass, baseX + 0.5, baseY, baseZ)
	If d2NPC <> Null Then
		SceneDClass2 = New SceneActor
		SceneDClass2\npc = d2NPC
		SceneDClass2\role = "D-7120"
		SceneDClass2\state = 0
		SceneDClass2\visible = True
	EndIf

	; D-9341 - glavnyi geroy originala
	Local d9341NPC.NPCs = CreateNPC(NPCtypeDClass, baseX, baseY, baseZ + 0.5)
	If d9341NPC <> Null Then
		SceneDClass9341 = New SceneActor
		SceneDClass9341\npc = d9341NPC
		SceneDClass9341\role = "D-9341"
		SceneDClass9341\state = 0
		SceneDClass9341\visible = True
	EndIf

	DebugLog "Day 2 actors spawned"
End Function

; --- КАТСЦЕНА У 173 ---

Function Start173Scene(room.Rooms)
	If ActiveScene <> Null Then Return

	ActiveScene = New ContainmentScene
	ActiveScene\state = SCENE_WAITING_PLAYER
	ActiveScene\timer = 0.0
	ActiveScene\phase = 0
	ActiveScene\room173 = room
	ActiveScene\playerPosition = 0
	ActiveScene\lightsFlickering = False
	ActiveScene\flickerCount = 0
	ActiveScene\flickerTimer = 0.0
	ActiveScene\announcementPlayed = False

	CanPlayerMove = False

	DebugLog "173 scene started"
End Function

Function Update173Scene()
	If ActiveScene = Null Then Return

	ActiveScene\timer = ActiveScene\timer + FPSfactor

	Select ActiveScene\state

		Case SCENE_WAITING_PLAYER
			If ActiveScene\timer > 35.0 Then
				ActiveScene\state = SCENE_INTRO
				ActiveScene\timer = 0.0
				StartDialog(120)
			EndIf

		Case SCENE_INTRO
			If Not DialogActive Then
				ActiveScene\state = SCENE_DCLASS_ENTER
				ActiveScene\timer = 0.0
				MoveActorsIntoCell()
			EndIf

		Case SCENE_DCLASS_ENTER
			If ActiveScene\timer > 140.0 Then
				ActiveScene\state = SCENE_ANNOUNCEMENT
				ActiveScene\timer = 0.0
				StartDialog(130)
				If IntroAnnouncementSFX <> 0 Then
					ActiveScene\intercomChannel = PlaySound(IntroAnnouncementSFX)
				EndIf
			EndIf

		Case SCENE_ANNOUNCEMENT
			If Not DialogActive Then
				ActiveScene\timer = ActiveScene\timer + FPSfactor
				If ActiveScene\timer > 70.0 Then
					StartDialog(131)
					ActiveScene\state = SCENE_LIGHTS_FLICKER
					ActiveScene\timer = 0.0
				EndIf
			EndIf

		Case SCENE_LIGHTS_FLICKER
			If Not DialogActive Then
				If ActiveScene\timer > 70.0 Then
					; nachalo miganiya
					ActiveScene\lightsFlickering = True
					ActiveScene\flickerCount = 0

					If LightsFlickerSFX <> 0 Then PlaySound(LightsFlickerSFX)

					StartDialog(140)
					ActiveScene\state = SCENE_LIGHTS_RESTORE
					ActiveScene\timer = 0.0
				EndIf
			EndIf

		Case SCENE_LIGHTS_RESTORE
			UpdateLightsFlicker()

			If Not DialogActive Then
				ActiveScene\lightsFlickering = False
				StartDialog(141)
				ActiveScene\state = SCENE_HARRISON_VOICE
				ActiveScene\timer = 0.0
			EndIf

		Case SCENE_HARRISON_VOICE
			If Not DialogActive Then
				If ActiveScene\timer > 35.0 Then
					If IntercomSFX <> 0 Then PlaySound(IntercomSFX)
					StartDialog(142)
					ActiveScene\state = SCENE_DCLASS_EXIT
					ActiveScene\timer = 0.0
				EndIf
			EndIf

		Case SCENE_DCLASS_EXIT
			If Not DialogActive Then
				MoveActorsOutOfCell()
				If ActiveScene\timer > 105.0 Then
					ActiveScene\state = SCENE_STEVE_RADIO
					ActiveScene\timer = 0.0
					StartDialog(143)
				EndIf
			EndIf

		Case SCENE_STEVE_RADIO
			If Not DialogActive Then
				ActiveScene\state = SCENE_COMPLETE
				ActiveScene\timer = 0.0
				CanPlayerMove = True
				SetStoryFlag(FLAG_WITNESSED_PROCEDURE, 1)
			EndIf

		Case SCENE_COMPLETE
			; scena zavershena
			CleanupScene()

	End Select
End Function

Function UpdateLightsFlicker()
	If ActiveScene = Null Then Return
	If Not ActiveScene\lightsFlickering Then Return

	ActiveScene\flickerTimer = ActiveScene\flickerTimer + FPSfactor

	If ActiveScene\flickerTimer > 7.0 Then
		ActiveScene\flickerTimer = 0.0
		ActiveScene\flickerCount = ActiveScene\flickerCount + 1

		; miganie osveshcheniya v komnate
		If ActiveScene\room173 <> Null Then
			For i% = 0 To MaxRoomLights - 1
				If ActiveScene\room173\Lights[i] <> 0 Then
					If ActiveScene\flickerCount Mod 2 = 0 Then
						HideEntity ActiveScene\room173\Lights[i]
					Else
						ShowEntity ActiveScene\room173\Lights[i]
					EndIf
				EndIf
			Next
		EndIf

		If ActiveScene\flickerCount >= 6 Then
			; vosstanovit' svet
			If ActiveScene\room173 <> Null Then
				For i% = 0 To MaxRoomLights - 1
					If ActiveScene\room173\Lights[i] <> 0 Then
						ShowEntity ActiveScene\room173\Lights[i]
					EndIf
				Next
			EndIf
			ActiveScene\lightsFlickering = False
		EndIf
	EndIf
End Function

Function MoveActorsIntoCell()
	If ActiveScene = Null Then Return
	If ActiveScene\room173 = Null Then Return

	Local cellX# = EntityX(ActiveScene\room173\obj)
	Local cellY# = 0.5
	Local cellZ# = EntityZ(ActiveScene\room173\obj)

	If SceneDClass1 <> Null And SceneDClass1\npc <> Null Then
		PositionEntity SceneDClass1\npc\Collider, cellX - 1.0, cellY, cellZ + 2.0
	EndIf

	If SceneDClass2 <> Null And SceneDClass2\npc <> Null Then
		PositionEntity SceneDClass2\npc\Collider, cellX + 1.0, cellY, cellZ + 2.0
	EndIf

	If SceneDClass9341 <> Null And SceneDClass9341\npc <> Null Then
		PositionEntity SceneDClass9341\npc\Collider, cellX, cellY, cellZ + 3.0
	EndIf
End Function

Function MoveActorsOutOfCell()
	If ActiveScene = Null Then Return
	If ActiveScene\room173 = Null Then Return

	Local exitX# = EntityX(ActiveScene\room173\obj)
	Local exitY# = 0.5
	Local exitZ# = EntityZ(ActiveScene\room173\obj) - 4.0

	If SceneDClass1 <> Null And SceneDClass1\npc <> Null Then
		PositionEntity SceneDClass1\npc\Collider, exitX - 1.0, exitY, exitZ
	EndIf

	If SceneDClass2 <> Null And SceneDClass2\npc <> Null Then
		PositionEntity SceneDClass2\npc\Collider, exitX + 1.0, exitY, exitZ
	EndIf

	If SceneDClass9341 <> Null And SceneDClass9341\npc <> Null Then
		PositionEntity SceneDClass9341\npc\Collider, exitX, exitY, exitZ - 1.0
	EndIf
End Function

Function CleanupScene()
	If ActiveScene <> Null Then
		Delete ActiveScene
		ActiveScene = Null
	EndIf

	; udalyaem actorov (oni ushli)
	If SceneDClass1 <> Null Then
		If SceneDClass1\npc <> Null Then RemoveNPC(SceneDClass1\npc)
		Delete SceneDClass1
		SceneDClass1 = Null
	EndIf

	If SceneDClass2 <> Null Then
		If SceneDClass2\npc <> Null Then RemoveNPC(SceneDClass2\npc)
		Delete SceneDClass2
		SceneDClass2 = Null
	EndIf

	If SceneDClass9341 <> Null Then
		If SceneDClass9341\npc <> Null Then RemoveNPC(SceneDClass9341\npc)
		Delete SceneDClass9341
		SceneDClass9341 = Null
	EndIf

	DebugLog "Scene cleanup complete"
End Function

; --- ФИНАЛ ДНЯ 2 ---

Function TriggerDay2Finale()
	If Not GetStoryFlag(FLAG_SAW_HARRISON_TERMINAL) Then Return

	SetStoryFlag(FLAG_DAY2_COMPLETE, 1)
	StartDialog(160)
End Function

Function OnDay2Complete()
	If GetStoryFlag(FLAG_DAY2_COMPLETE) And Not DialogActive Then
		TriggerDayTransition(3)
	EndIf
End Function

; --- СУБТИТРЫ ---

Function ShowSubtitle(speaker$, text$, duration#)
	SubtitleSpeaker = speaker
	SubtitleText = text
	SubtitleTimer = duration
End Function

Function UpdateSubtitles()
	If SubtitleTimer > 0.0 Then
		SubtitleTimer = SubtitleTimer - FPSfactor
		If SubtitleTimer <= 0.0 Then
			SubtitleText = ""
			SubtitleSpeaker = ""
		EndIf
	EndIf
End Function

Function RenderSubtitles()
	If SubtitleText = "" Then Return

	Local gw% = GraphicsWidth()
	Local gh% = GraphicsHeight()

	Local boxW% = gw - 200
	Local boxH% = 50
	Local boxX% = 100
	Local boxY% = gh - 100

	Color 0, 0, 0
	Rect boxX, boxY, boxW, boxH, True

	Color 80, 80, 80
	Rect boxX, boxY, boxW, boxH, False

	If SubtitleSpeaker <> "" Then
		Color 180, 150, 50
		Text boxX + 10, boxY + 5, SubtitleSpeaker + ":"
	EndIf

	Color 220, 220, 220
	Text boxX + 10, boxY + 22, SubtitleText
End Function

; --- HOOK DLYa UpdateProjectMirror ---

Function UpdateDay2Logic()
	If CurrentDay <> 2 Then Return

	; init dnya 2
	If Not GetStoryFlag(FLAG_DAY2_STARTED) Then
		StartDay2Intro()
	EndIf

	; update katstseny
	If ActiveScene <> Null Then
		Update173Scene()
	EndIf

	; subtitry
	UpdateSubtitles()

	; proverka finala
	OnDay2Complete()
End Function
