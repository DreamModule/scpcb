;===============================================================================
; PROJECT MIRROR: STORY STATE & DIALOG SYSTEM
; Blitz3D Module for SCP: Containment Breach
; Target: Blitz3D 1.108 / Legacy Compatibility
;===============================================================================

;-------------------------------------------------------------------------------
; CONSTANTS
;-------------------------------------------------------------------------------
Const MAX_DIALOG_OPTIONS% = 6
Const MAX_STORY_FLAGS% = 64
Const MAX_DIALOG_NODES% = 256

Const STORY_BRANCH_NEUTRAL% = 0
Const STORY_BRANCH_REDEMPTION% = 1
Const STORY_BRANCH_CHAOS% = 2
Const STORY_BRANCH_SACRIFICE% = 3

Const KARMA_MIN% = -100
Const KARMA_MAX% = 100

;-------------------------------------------------------------------------------
; GLOBAL STORY STATE
;-------------------------------------------------------------------------------
Global CurrentDay% = 1
Global CurrentKarma% = 0
Global StoryBranch% = STORY_BRANCH_NEUTRAL
Global CanPlayerMove% = True
Global DialogActive% = False
Global CurrentDialogNode.DialogNode = Null

; Critical story flags - packed into array for memory efficiency
Dim StoryFlags%(MAX_STORY_FLAGS)

; Flag indices for key events
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

; Day transition tracking
Global DayTransitionPending% = False
Global DayTransitionTimer# = 0.0
Global DayTransitionFade# = 0.0

; Has Harrison PDA - critical for 914 quest
Global HasHarrisonPDA% = False
Global HasHarrisonEye% = False

;-------------------------------------------------------------------------------
; TYPE: STORY STATE (Singleton pattern via Global)
;-------------------------------------------------------------------------------
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

;-------------------------------------------------------------------------------
; TYPE: DIALOG NODE
;-------------------------------------------------------------------------------
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

;-------------------------------------------------------------------------------
; TYPE: DIALOG OPTION
;-------------------------------------------------------------------------------
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

;-------------------------------------------------------------------------------
; TYPE: DIALOG EVENT RECORD (for Echo System)
;-------------------------------------------------------------------------------
Type DialogEventRecord
	Field day%
	Field roomName$
	Field roomX#
	Field roomY#
	Field roomZ#
	Field speakerName$
	Field soundPath$
	Field animationName$
	Field timestamp#
End Type

;-------------------------------------------------------------------------------
; DIALOG RENDERING GLOBALS
;-------------------------------------------------------------------------------
Global DialogBoxTexture% = 0
Global DialogFont% = 0
Global DialogSelectedOption% = 0
Global DialogTypewriterSpeed# = 0.05

Dim DialogOptions.DialogOption(MAX_DIALOG_OPTIONS)
Dim DialogNodeCache.DialogNode(MAX_DIALOG_NODES)

;===============================================================================
; INITIALIZATION
;===============================================================================
Function InitStorySystem()
	; Create singleton story state
	If GStoryState = Null Then
		GStoryState = New StoryState
		GStoryState\day = 1
		GStoryState\karma = 0
		GStoryState\branch = STORY_BRANCH_NEUTRAL
		GStoryState\playTime = 0.0
		GStoryState\deathCount = 0
		GStoryState\checkpointRoom = ""
	EndIf

	; Reset all story flags
	For i% = 0 To MAX_STORY_FLAGS - 1
		StoryFlags(i) = 0
	Next

	; Sync globals with story state
	CurrentDay = GStoryState\day
	CurrentKarma = GStoryState\karma
	StoryBranch = GStoryState\branch

	; Reset dialog state
	DialogActive = False
	CurrentDialogNode = Null
	DialogSelectedOption = 0
	CanPlayerMove = True

	; Clear dialog option references
	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		DialogOptions(i) = Null
	Next

	HasHarrisonPDA = False
	HasHarrisonEye = False
End Function

;===============================================================================
; FLAG MANAGEMENT
;===============================================================================
Function SetStoryFlag(flagIndex%, value% = 1)
	If flagIndex >= 0 And flagIndex < MAX_STORY_FLAGS Then
		StoryFlags(flagIndex) = value

		; Sync special flags with globals
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

;===============================================================================
; KARMA SYSTEM
;===============================================================================
Function ModifyKarma(amount%)
	CurrentKarma = CurrentKarma + amount

	; Clamp karma to valid range
	If CurrentKarma < KARMA_MIN Then CurrentKarma = KARMA_MIN
	If CurrentKarma > KARMA_MAX Then CurrentKarma = KARMA_MAX

	; Sync with story state
	If GStoryState <> Null Then
		GStoryState\karma = CurrentKarma
	EndIf

	; Auto-determine branch based on karma thresholds
	UpdateStoryBranch()
End Function

Function UpdateStoryBranch()
	Local oldBranch% = StoryBranch

	; Branch determination logic
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
	If CurrentKarma >= 75 Then Return 3      ; Saint
	If CurrentKarma >= 25 Then Return 2      ; Good
	If CurrentKarma >= -25 Then Return 1     ; Neutral
	If CurrentKarma >= -75 Then Return 0     ; Bad
	Return -1                                 ; Evil
End Function

;===============================================================================
; DAY TRANSITION SYSTEM
;===============================================================================
Function TriggerDayTransition(newDay%)
	If newDay > 0 And newDay <= 3 And newDay > CurrentDay Then
		DayTransitionPending = True
		DayTransitionTimer = 0.0
		DayTransitionFade = 0.0

		; Store pending day
		CurrentDay = newDay
		If GStoryState <> Null Then
			GStoryState\day = newDay
		EndIf
	EndIf
End Function

Function UpdateDayTransition()
	If Not DayTransitionPending Then Return

	; FPSfactor is global from Main.bb
	DayTransitionTimer = DayTransitionTimer + FPSfactor

	; Fade out phase (0-70 frames = 1 second)
	If DayTransitionTimer < 70.0 Then
		DayTransitionFade = DayTransitionTimer / 70.0
		CanPlayerMove = False
	; Hold black phase (70-210 frames = 2 seconds)
	ElseIf DayTransitionTimer < 210.0 Then
		DayTransitionFade = 1.0
	; Fade in phase (210-280 frames = 1 second)
	ElseIf DayTransitionTimer < 280.0 Then
		DayTransitionFade = 1.0 - ((DayTransitionTimer - 210.0) / 70.0)
	Else
		; Transition complete
		DayTransitionPending = False
		DayTransitionFade = 0.0
		CanPlayerMove = True
	EndIf
End Function

Function RenderDayTransition()
	If Not DayTransitionPending Then Return
	If DayTransitionFade <= 0.0 Then Return

	; Draw fade overlay
	Color 0, 0, 0
	Local alpha% = Int(DayTransitionFade * 255.0)

	; Blitz3D doesn't have alpha rects, use multiple lines or texture
	; For now, solid black when alpha > 0.5
	If DayTransitionFade > 0.5 Then
		Rect 0, 0, GraphicsWidth(), GraphicsHeight(), True

		; Draw day text during hold phase
		If DayTransitionTimer >= 70.0 And DayTransitionTimer < 210.0 Then
			Color 200, 200, 200
			Local dayText$ = "DAY " + CurrentDay
			Local tw% = StringWidth(dayText)
			Text (GraphicsWidth() - tw) / 2, GraphicsHeight() / 2 - 20, dayText

			; Subtitle based on day
			Local subtitle$ = ""
			Select CurrentDay
				Case 1
					subtitle = "THE BREACH"
				Case 2
					subtitle = "THE HUNT"
				Case 3
					subtitle = "THE RECKONING"
			End Select

			Color 150, 150, 150
			tw = StringWidth(subtitle)
			Text (GraphicsWidth() - tw) / 2, GraphicsHeight() / 2 + 10, subtitle
		EndIf
	EndIf
End Function

;===============================================================================
; DIALOG SYSTEM - NODE CREATION
;===============================================================================
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

	; Load portrait if specified
	If portraitPath <> "" Then
		node\portrait = LoadImage(portraitPath)
		If node\portrait <> 0 Then
			MaskImage node\portrait, 255, 0, 255
		EndIf
	EndIf

	; Cache node by ID for lookup
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

	; Store in array for rendering
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

;===============================================================================
; DIALOG SYSTEM - RUNTIME
;===============================================================================
Function StartDialog(nodeID%)
	If nodeID < 0 Or nodeID >= MAX_DIALOG_NODES Then Return

	Local node.DialogNode = DialogNodeCache(nodeID)
	If node = Null Then Return

	CurrentDialogNode = node
	DialogActive = True
	CanPlayerMove = False
	DialogSelectedOption = 0

	; Reset typewriter effect
	node\typewriterPos = 0
	node\typewriterTimer = 0.0
	node\displayedText = ""

	; Clear and populate options array
	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		DialogOptions(i) = Null
	Next

	; Find options for this node
	Local optIndex% = 0
	For opt.DialogOption = Each DialogOption
		If opt\parentNode = node Then
			; Check visibility based on required flags
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

	; Play voice if available
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

	; Typewriter effect update
	node\typewriterTimer = node\typewriterTimer + FPSfactor * DialogTypewriterSpeed
	If node\typewriterTimer >= 1.0 Then
		node\typewriterTimer = node\typewriterTimer - 1.0
		If node\typewriterPos < Len(node\text) Then
			node\typewriterPos = node\typewriterPos + 1
			node\displayedText = Left$(node\text, node\typewriterPos)
		EndIf
	EndIf

	; Input handling
	If KeyHit(200) Then ; Up arrow
		DialogSelectedOption = DialogSelectedOption - 1
		If DialogSelectedOption < 0 Then
			DialogSelectedOption = CountVisibleOptions() - 1
		EndIf
		; Skip invisible options
		While DialogOptions(DialogSelectedOption) = Null And DialogSelectedOption > 0
			DialogSelectedOption = DialogSelectedOption - 1
		Wend
	EndIf

	If KeyHit(208) Then ; Down arrow
		DialogSelectedOption = DialogSelectedOption + 1
		If DialogSelectedOption >= CountVisibleOptions() Then
			DialogSelectedOption = 0
		EndIf
		; Skip invisible options
		While DialogOptions(DialogSelectedOption) = Null And DialogSelectedOption < MAX_DIALOG_OPTIONS - 1
			DialogSelectedOption = DialogSelectedOption + 1
		Wend
	EndIf

	; Skip typewriter with space
	If KeyHit(57) Then ; Space
		If node\typewriterPos < Len(node\text) Then
			node\typewriterPos = Len(node\text)
			node\displayedText = node\text
		EndIf
	EndIf

	; Confirm selection with Enter or E
	If KeyHit(28) Or KeyHit(18) Then ; Enter or E
		If node\typewriterPos >= Len(node\text) Then
			SelectDialogOption()
		Else
			; Skip to end of text
			node\typewriterPos = Len(node\text)
			node\displayedText = node\text
		EndIf
	EndIf

	; Auto-advance for terminal nodes
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

	; Minimum 1 for terminal nodes (implicit "Continue")
	If count = 0 Then count = 1
	Return count
End Function

Function SelectDialogOption()
	Local opt.DialogOption = DialogOptions(DialogSelectedOption)

	If opt <> Null Then
		; Apply karma change
		If opt\karmaChange <> 0 Then
			ModifyKarma(opt\karmaChange)
		EndIf

		; Set flag if specified
		If opt\flagToSet >= 0 Then
			SetStoryFlag(opt\flagToSet, opt\flagValue)
		EndIf

		; Change branch if specified
		If opt\branchChange >= 0 Then
			StoryBranch = opt\branchChange
			If GStoryState <> Null Then
				GStoryState\branch = StoryBranch
			EndIf
		EndIf

		; Navigate to next node or end
		If opt\nextNodeID >= 0 Then
			StartDialog(opt\nextNodeID)
		Else
			EndDialog()
		EndIf
	Else
		; Terminal node - just end
		EndDialog()
	EndIf
End Function

Function EndDialog()
	If CurrentDialogNode <> Null Then
		; Stop voice playback
		If CurrentDialogNode\voiceChannel <> 0 Then
			StopChannel CurrentDialogNode\voiceChannel
		EndIf
	EndIf

	DialogActive = False
	CurrentDialogNode = Null
	CanPlayerMove = True
	DialogSelectedOption = 0

	; Clear options array
	For i% = 0 To MAX_DIALOG_OPTIONS - 1
		DialogOptions(i) = Null
	Next
End Function

;===============================================================================
; DIALOG RENDERING
;===============================================================================
Function RenderDialog()
	If Not DialogActive Then Return
	If CurrentDialogNode = Null Then Return

	Local node.DialogNode = CurrentDialogNode
	Local gw% = GraphicsWidth()
	Local gh% = GraphicsHeight()

	; Dialog box dimensions
	Local boxWidth% = gw - 100
	Local boxHeight% = 200
	Local boxX% = 50
	Local boxY% = gh - boxHeight - 30

	; Draw semi-transparent background
	Color 0, 0, 0
	Rect boxX, boxY, boxWidth, boxHeight, True

	; Draw border
	Color 100, 100, 100
	Rect boxX, boxY, boxWidth, boxHeight, False
	Rect boxX + 1, boxY + 1, boxWidth - 2, boxHeight - 2, False

	; Draw portrait if available
	Local textStartX% = boxX + 20
	If node\portrait <> 0 Then
		DrawImage node\portrait, boxX + 10, boxY + 10
		textStartX = boxX + 110
	EndIf

	; Draw speaker name
	Color 200, 180, 100
	Text textStartX, boxY + 15, node\speakerName

	; Draw dialog text with word wrap
	Color 220, 220, 220
	Local textY% = boxY + 40
	Local maxWidth% = boxWidth - (textStartX - boxX) - 20

	DrawWrappedText(node\displayedText, textStartX, textY, maxWidth)

	; Draw options if text is complete
	If node\typewriterPos >= Len(node\text) Then
		Local optY% = boxY + boxHeight - 80
		Local optIndex% = 0

		For i% = 0 To MAX_DIALOG_OPTIONS - 1
			Local opt.DialogOption = DialogOptions(i)
			If opt <> Null Then
				; Highlight selected option
				If optIndex = DialogSelectedOption Then
					Color 255, 200, 50
					Text textStartX - 15, optY, ">"
				Else
					Color 180, 180, 180
				EndIf

				; Draw option text
				Text textStartX, optY, (optIndex + 1) + ". " + opt\text

				; Show karma indicator
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

		; If no options, show continue prompt
		If optIndex = 0 Then
			Color 150, 150, 150
			Text textStartX, optY, "[Press ENTER to continue]"
		EndIf
	Else
		; Show skip prompt
		Color 100, 100, 100
		Text textStartX, boxY + boxHeight - 25, "[SPACE to skip]"
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

	; Draw remaining line
	If Len(line) > 0 Then
		Text x, lineY, line
	EndIf
End Function

;===============================================================================
; EVENT RECORDING (for Echo System)
;===============================================================================
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

;===============================================================================
; SAVE/LOAD SUPPORT
;===============================================================================
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

	; Save all flags
	For i% = 0 To MAX_STORY_FLAGS - 1
		WriteInt file, StoryFlags(i)
	Next

	; Save dialog event records for Echo system
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

	; Sync globals
	GStoryState\day = CurrentDay
	GStoryState\karma = CurrentKarma
	GStoryState\branch = StoryBranch

	; Load all flags
	For i% = 0 To MAX_STORY_FLAGS - 1
		StoryFlags(i) = ReadInt(file)
	Next

	; Sync special flags
	HasHarrisonPDA = StoryFlags(FLAG_HARRISON_PDA)
	HasHarrisonEye = StoryFlags(FLAG_HARRISON_EYE)

	; Load dialog event records
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
End Function

;===============================================================================
; CLEANUP
;===============================================================================
Function CleanupStorySystem()
	; Free portraits
	For node.DialogNode = Each DialogNode
		If node\portrait <> 0 Then
			FreeImage node\portrait
		EndIf
		Delete node
	Next

	; Delete all options
	For opt.DialogOption = Each DialogOption
		Delete opt
	Next

	; Delete event records
	For rec.DialogEventRecord = Each DialogEventRecord
		Delete rec
	Next

	; Clear cache
	For i% = 0 To MAX_DIALOG_NODES - 1
		DialogNodeCache(i) = Null
	Next

	; Delete story state
	If GStoryState <> Null Then
		Delete GStoryState
		GStoryState = Null
	EndIf
End Function

;===============================================================================
; DEBUG
;===============================================================================
Function DebugStoryState()
	Color 255, 255, 255
	Text 10, 10, "=== PROJECT MIRROR DEBUG ==="
	Text 10, 30, "Day: " + CurrentDay
	Text 10, 45, "Karma: " + CurrentKarma + " (Level: " + GetKarmaLevel() + ")"
	Text 10, 60, "Branch: " + GetBranchName(StoryBranch)
	Text 10, 75, "Dialog Active: " + DialogActive
	Text 10, 90, "Can Move: " + CanPlayerMove

	Local flagY% = 110
	Text 10, flagY, "--- Flags ---"
	flagY = flagY + 15

	If GetStoryFlag(FLAG_STEVE_MET) Then Text 10, flagY, "Steve Met" : flagY = flagY + 12
	If GetStoryFlag(FLAG_STEVE_SAVED) Then Text 10, flagY, "Steve Saved" : flagY = flagY + 12
	If GetStoryFlag(FLAG_STEVE_DEAD) Then Text 10, flagY, "Steve Dead" : flagY = flagY + 12
	If GetStoryFlag(FLAG_HARRISON_PDA) Then Text 10, flagY, "Harrison PDA" : flagY = flagY + 12
	If GetStoryFlag(FLAG_HARRISON_EYE) Then Text 10, flagY, "Harrison Eye" : flagY = flagY + 12
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
