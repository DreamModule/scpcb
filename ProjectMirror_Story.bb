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

	; инит диалогов Дня 1
	SetupDay1Dialogs()
	SetupDay1Triggers()
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
					subtitle = "ПРОРЫВ"
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
