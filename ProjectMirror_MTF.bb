;===============================================================================
; PROJECT MIRROR: MTF "FOXES" TACTICAL AI
; Blitz3D Module for SCP: Containment Breach
; Advanced Encirclement and Flashbang Tactics
;===============================================================================

;-------------------------------------------------------------------------------
; CONSTANTS
;-------------------------------------------------------------------------------
Const MTF_FOX_MAX_SQUAD% = 6
Const MTF_FOX_FLANK_DISTANCE# = 8.0
Const MTF_FOX_ENGAGE_DISTANCE# = 15.0
Const MTF_FOX_ENGAGE_DISTANCE_SQ# = 225.0
Const MTF_FOX_CLOSE_DISTANCE# = 4.0
Const MTF_FOX_CLOSE_DISTANCE_SQ# = 16.0

; Tactical states
Const FOX_STATE_PATROL% = 0
Const FOX_STATE_ALERT% = 1
Const FOX_STATE_FLANKING% = 2
Const FOX_STATE_ENGAGING% = 3
Const FOX_STATE_FLASHBANG% = 4
Const FOX_STATE_BREACH% = 5
Const FOX_STATE_CONTAIN% = 6

; Roles in squad
Const FOX_ROLE_LEADER% = 0
Const FOX_ROLE_POINTMAN% = 1
Const FOX_ROLE_FLANKER_L% = 2
Const FOX_ROLE_FLANKER_R% = 3
Const FOX_ROLE_REAR% = 4
Const FOX_ROLE_SUPPORT% = 5

; Flashbang parameters
Const FLASHBANG_RANGE# = 6.0
Const FLASHBANG_RANGE_SQ# = 36.0
Const FLASHBANG_DURATION# = 280.0  ; 4 seconds at 70fps
Const FLASHBANG_STUN_DURATION# = 140.0  ; 2 seconds

; Line of sight parameters
Const LOS_CHECK_INTERVAL# = 7.0  ; Check every 7 frames
Const SHADOW_THRESHOLD# = 0.3    ; Light level below this = shadow

;-------------------------------------------------------------------------------
; TYPE: FOX TACTICAL STATE
;-------------------------------------------------------------------------------
Type MTFFoxState
	Field npcRef.NPCs
	Field tacticalState%
	Field role%
	Field squad.MTFFoxSquad

	; Positioning
	Field targetX#, targetY#, targetZ#
	Field flankAngle#
	Field coverPoint%              ; Pivot for cover position

	; Timers
	Field stateTimer#
	Field losCheckTimer#
	Field flashbangCooldown#
	Field repositionTimer#

	; Detection
	Field hasLOS%                  ; Line of sight to player
	Field lastSeenX#, lastSeenY#, lastSeenZ#
	Field lastSeenTime%
	Field playerInShadow%

	; Communication
	Field signalSent%
	Field waitingForSignal%
End Type

;-------------------------------------------------------------------------------
; TYPE: FOX SQUAD
;-------------------------------------------------------------------------------
Type MTFFoxSquad
	Field id%
	Field leader.MTFFoxState
	Field members.MTFFoxState[MTF_FOX_MAX_SQUAD]
	Field memberCount%

	; Squad state
	Field alertLevel%              ; 0-100
	Field playerLastKnownX#
	Field playerLastKnownY#
	Field playerLastKnownZ#
	Field engagementActive%
	Field encirclementPhase%       ; 0=none, 1=spreading, 2=closing

	; Tactics
	Field flashbangQueued%
	Field flashbangUser.MTFFoxState
	Field breachTarget.Doors
End Type

;-------------------------------------------------------------------------------
; GLOBALS
;-------------------------------------------------------------------------------
Global FoxTacticsEnabled% = True
Global FoxSquadCount% = 0
Global ActiveFoxSquad.MTFFoxSquad = Null

; Flashbang effect state
Global FlashbangActive% = False
Global FlashbangIntensity# = 0.0
Global FlashbangTimer# = 0.0
Global PlayerStunned% = False
Global PlayerStunTimer# = 0.0

; Player visibility tracking
Global PlayerLightLevel# = 1.0
Global PlayerInCover% = False
Global PlayerCoverEntity% = 0

; Sound effects
Global FlashbangSFX% = 0
Global FlashbangRingSFX% = 0
Global TacticalRadioSFX%[4]

Dim FoxSquads.MTFFoxSquad(4)

;===============================================================================
; INITIALIZATION
;===============================================================================
Function InitFoxTactics()
	; Load sounds
	FlashbangSFX = LoadSound("SFX\General\Flashbang.ogg")
	FlashbangRingSFX = LoadSound("SFX\General\EarRing.ogg")

	TacticalRadioSFX(0) = LoadSound("SFX\Character\MTF\TacticalGo.ogg")
	TacticalRadioSFX(1) = LoadSound("SFX\Character\MTF\TacticalFlank.ogg")
	TacticalRadioSFX(2) = LoadSound("SFX\Character\MTF\TacticalFlash.ogg")
	TacticalRadioSFX(3) = LoadSound("SFX\Character\MTF\TacticalContact.ogg")

	FoxSquadCount = 0
	ActiveFoxSquad = Null

	FlashbangActive = False
	FlashbangIntensity = 0.0
	PlayerStunned = False

	FoxTacticsEnabled = True
End Function

;===============================================================================
; SQUAD MANAGEMENT
;===============================================================================
Function CreateFoxSquad.MTFFoxSquad()
	If FoxSquadCount >= 4 Then Return Null

	Local squad.MTFFoxSquad = New MTFFoxSquad
	squad\id = FoxSquadCount
	squad\memberCount = 0
	squad\alertLevel = 0
	squad\engagementActive = False
	squad\encirclementPhase = 0
	squad\flashbangQueued = False
	squad\leader = Null

	For i% = 0 To MTF_FOX_MAX_SQUAD - 1
		squad\members[i] = Null
	Next

	FoxSquads(FoxSquadCount) = squad
	FoxSquadCount = FoxSquadCount + 1

	Return squad
End Function

Function AddFoxToSquad(squad.MTFFoxSquad, n.NPCs, role%)
	If squad = Null Or n = Null Then Return
	If squad\memberCount >= MTF_FOX_MAX_SQUAD Then Return
	If n\NPCtype <> NPCtypeMTF Then Return

	Local fox.MTFFoxState = New MTFFoxState
	fox\npcRef = n
	fox\squad = squad
	fox\role = role
	fox\tacticalState = FOX_STATE_PATROL
	fox\stateTimer = 0.0
	fox\losCheckTimer = 0.0
	fox\flashbangCooldown = 0.0
	fox\repositionTimer = 0.0
	fox\hasLOS = False
	fox\signalSent = False
	fox\waitingForSignal = False
	fox\playerInShadow = False

	; Create cover pivot
	fox\coverPoint = CreatePivot()

	squad\members[squad\memberCount] = fox
	squad\memberCount = squad\memberCount + 1

	; First member becomes leader
	If role = FOX_ROLE_LEADER Or squad\leader = Null Then
		squad\leader = fox
	EndIf
End Function

Function GetFoxState.MTFFoxState(n.NPCs)
	For squad.MTFFoxSquad = Each MTFFoxSquad
		For i% = 0 To squad\memberCount - 1
			If squad\members[i] <> Null Then
				If squad\members[i]\npcRef = n Then
					Return squad\members[i]
				EndIf
			EndIf
		Next
	Next
	Return Null
End Function

;===============================================================================
; MAIN TACTICAL UPDATE
;===============================================================================
Function UpdateFoxTactics()
	If Not FoxTacticsEnabled Then Return

	; Only active on Day 3 or in finale
	If CurrentDay < 3 And Not GetStoryFlag(FLAG_FINALE_TRIGGERED) Then Return

	; Update each squad
	For squad.MTFFoxSquad = Each MTFFoxSquad
		UpdateFoxSquad(squad)
	Next

	; Update flashbang effect
	UpdateFlashbangEffect()

	; Update player stun
	UpdatePlayerStun()
End Function

Function UpdateFoxSquad(squad.MTFFoxSquad)
	If squad = Null Then Return
	If squad\memberCount = 0 Then Return

	; Calculate squad alert level based on members
	Local totalLOS% = 0
	Local closestDistSq# = 100000.0

	For i% = 0 To squad\memberCount - 1
		Local fox.MTFFoxState = squad\members[i]
		If fox = Null Then Continue
		If fox\npcRef = Null Then Continue

		; Individual fox update
		UpdateFoxMember(fox)

		; Track LOS
		If fox\hasLOS Then totalLOS = totalLOS + 1

		; Track closest distance
		Local distSq# = GetDistanceSquaredToPlayer(fox\npcRef)
		If distSq < closestDistSq Then closestDistSq = distSq
	Next

	; Update squad alert level
	If totalLOS > 0 Then
		squad\alertLevel = Min(squad\alertLevel + 5, 100)

		; Update last known position
		squad\playerLastKnownX = EntityX(Collider, True)
		squad\playerLastKnownY = EntityY(Collider, True)
		squad\playerLastKnownZ = EntityZ(Collider, True)
	Else
		squad\alertLevel = Max(squad\alertLevel - 1, 0)
	EndIf

	; Tactical decision making
	If squad\alertLevel >= 50 And Not squad\engagementActive Then
		; Start engagement
		InitiateEngagement(squad)
	EndIf

	If squad\engagementActive Then
		UpdateEncirclement(squad, closestDistSq)
	EndIf
End Function

;===============================================================================
; INDIVIDUAL FOX UPDATE
;===============================================================================
Function UpdateFoxMember(fox.MTFFoxState)
	If fox = Null Then Return
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef

	; Update timers
	fox\stateTimer = fox\stateTimer + FPSfactor
	fox\losCheckTimer = fox\losCheckTimer + FPSfactor

	If fox\flashbangCooldown > 0 Then
		fox\flashbangCooldown = fox\flashbangCooldown - FPSfactor
	EndIf

	If fox\repositionTimer > 0 Then
		fox\repositionTimer = fox\repositionTimer - FPSfactor
	EndIf

	; Periodic LOS check
	If fox\losCheckTimer >= LOS_CHECK_INTERVAL Then
		fox\losCheckTimer = 0.0
		fox\hasLOS = CheckLineOfSight(n, Collider)
		fox\playerInShadow = CheckPlayerInShadow()
	EndIf

	; State machine
	Select fox\tacticalState
		;-----------------------------------------------------------------------
		Case FOX_STATE_PATROL
			; Standard patrol - let original AI handle
			; Check for player detection
			If fox\hasLOS Then
				fox\tacticalState = FOX_STATE_ALERT
				fox\stateTimer = 0.0

				; Signal squad
				If fox\squad <> Null And Not fox\signalSent Then
					SignalSquad(fox\squad, fox)
					fox\signalSent = True
				EndIf
			EndIf

		;-----------------------------------------------------------------------
		Case FOX_STATE_ALERT
			; Player spotted - wait for squad coordination
			If fox\stateTimer > 35.0 Then  ; 0.5 second reaction time
				If fox\squad\engagementActive Then
					; Transition based on role
					Select fox\role
						Case FOX_ROLE_LEADER
							fox\tacticalState = FOX_STATE_ENGAGING
						Case FOX_ROLE_FLANKER_L, FOX_ROLE_FLANKER_R
							fox\tacticalState = FOX_STATE_FLANKING
						Case FOX_ROLE_POINTMAN
							fox\tacticalState = FOX_STATE_ENGAGING
						Case FOX_ROLE_SUPPORT
							fox\tacticalState = FOX_STATE_FLASHBANG
						Default
							fox\tacticalState = FOX_STATE_ENGAGING
					End Select
					fox\stateTimer = 0.0
				EndIf
			EndIf

		;-----------------------------------------------------------------------
		Case FOX_STATE_FLANKING
			; Move to flank position
			UpdateFlankingBehavior(fox)

		;-----------------------------------------------------------------------
		Case FOX_STATE_ENGAGING
			; Direct engagement
			UpdateEngagingBehavior(fox)

		;-----------------------------------------------------------------------
		Case FOX_STATE_FLASHBANG
			; Prepare and throw flashbang
			UpdateFlashbangBehavior(fox)

		;-----------------------------------------------------------------------
		Case FOX_STATE_BREACH
			; Door breach behavior
			UpdateBreachBehavior(fox)

		;-----------------------------------------------------------------------
		Case FOX_STATE_CONTAIN
			; Hold position and contain
			UpdateContainBehavior(fox)
	End Select
End Function

;===============================================================================
; TACTICAL BEHAVIORS
;===============================================================================
Function UpdateFlankingBehavior(fox.MTFFoxState)
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef
	Local squad.MTFFoxSquad = fox\squad

	If squad = Null Then
		fox\tacticalState = FOX_STATE_ENGAGING
		Return
	EndIf

	; Calculate flank position
	Local playerX# = squad\playerLastKnownX
	Local playerZ# = squad\playerLastKnownZ

	; Determine flank angle based on role
	Local baseAngle# = ATan2(playerX - EntityX(n\Collider, True), playerZ - EntityZ(n\Collider, True))

	If fox\role = FOX_ROLE_FLANKER_L Then
		fox\flankAngle = baseAngle + 60.0  ; 60 degrees left
	ElseIf fox\role = FOX_ROLE_FLANKER_R Then
		fox\flankAngle = baseAngle - 60.0  ; 60 degrees right
	EndIf

	; Calculate target position
	fox\targetX = playerX + Cos(fox\flankAngle) * MTF_FOX_FLANK_DISTANCE
	fox\targetZ = playerZ + Sin(fox\flankAngle) * MTF_FOX_FLANK_DISTANCE
	fox\targetY = EntityY(n\Collider, True)

	; Move toward flank position
	MoveTowardTarget(fox)

	; Check if reached flank position
	Local dx# = fox\targetX - EntityX(n\Collider, True)
	Local dz# = fox\targetZ - EntityZ(n\Collider, True)
	Local distSq# = dx*dx + dz*dz

	If distSq < 4.0 Then  ; Within 2 units
		; Flank complete - engage
		fox\tacticalState = FOX_STATE_ENGAGING
		fox\stateTimer = 0.0

		; Signal ready
		If squad\leader <> Null Then
			squad\encirclementPhase = 2  ; Ready to close
		EndIf
	EndIf

	; Timeout - engage anyway after 10 seconds
	If fox\stateTimer > 700.0 Then
		fox\tacticalState = FOX_STATE_ENGAGING
		fox\stateTimer = 0.0
	EndIf
End Function

Function UpdateEngagingBehavior(fox.MTFFoxState)
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef
	Local distSq# = GetDistanceSquaredToPlayer(n)

	; Check if player is in shadow/cover
	If fox\playerInShadow And fox\flashbangCooldown <= 0 Then
		; Player hiding - switch to flashbang
		fox\tacticalState = FOX_STATE_FLASHBANG
		fox\stateTimer = 0.0
		Return
	EndIf

	; Move toward player
	fox\targetX = EntityX(Collider, True)
	fox\targetY = EntityY(Collider, True)
	fox\targetZ = EntityZ(Collider, True)

	; Keep some distance if other squad members are flanking
	If fox\squad\encirclementPhase = 1 Then
		; Wait for flankers
		If distSq < MTF_FOX_ENGAGE_DISTANCE_SQ Then
			; Hold position
			Return
		EndIf
	EndIf

	MoveTowardTarget(fox)

	; Close distance for containment
	If distSq < MTF_FOX_CLOSE_DISTANCE_SQ Then
		fox\tacticalState = FOX_STATE_CONTAIN
		fox\stateTimer = 0.0
	EndIf
End Function

Function UpdateFlashbangBehavior(fox.MTFFoxState)
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef
	Local distSq# = GetDistanceSquaredToPlayer(n)

	; Check range for flashbang
	If distSq > FLASHBANG_RANGE_SQ * 4.0 Then
		; Too far - move closer
		fox\targetX = EntityX(Collider, True)
		fox\targetY = EntityY(Collider, True)
		fox\targetZ = EntityZ(Collider, True)
		MoveTowardTarget(fox)
		Return
	EndIf

	; Prepare flashbang
	If fox\stateTimer < 70.0 Then
		; Wind up animation / voice line
		If fox\stateTimer < 5.0 Then
			; Play radio call
			If TacticalRadioSFX(2) <> 0 Then
				PlaySound TacticalRadioSFX(2)
			EndIf
		EndIf
		Return
	EndIf

	; Throw flashbang
	ThrowFlashbang(fox)

	; Enter cooldown and switch to engaging
	fox\flashbangCooldown = 1400.0  ; 20 second cooldown
	fox\tacticalState = FOX_STATE_ENGAGING
	fox\stateTimer = 0.0
End Function

Function UpdateBreachBehavior(fox.MTFFoxState)
	If fox\npcRef = Null Then Return

	; Door breach - not implemented yet, fall back to engaging
	fox\tacticalState = FOX_STATE_ENGAGING
	fox\stateTimer = 0.0
End Function

Function UpdateContainBehavior(fox.MTFFoxState)
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef
	Local distSq# = GetDistanceSquaredToPlayer(n)

	; Maintain close distance but don't rush
	If distSq > MTF_FOX_CLOSE_DISTANCE_SQ * 2.0 Then
		; Player escaping - pursue
		fox\tacticalState = FOX_STATE_ENGAGING
		fox\stateTimer = 0.0
		Return
	EndIf

	; Face player
	Local playerX# = EntityX(Collider, True)
	Local playerZ# = EntityZ(Collider, True)
	Local npcX# = EntityX(n\Collider, True)
	Local npcZ# = EntityZ(n\Collider, True)
	Local angle# = ATan2(playerX - npcX, playerZ - npcZ)

	; Slow rotation toward player
	Local currentAngle# = EntityYaw(n\Collider)
	Local angleDiff# = angle - currentAngle

	; Normalize angle difference
	While angleDiff > 180.0
		angleDiff = angleDiff - 360.0
	Wend
	While angleDiff < -180.0
		angleDiff = angleDiff + 360.0
	Wend

	RotateEntity n\Collider, 0, currentAngle + angleDiff * 0.1 * FPSfactor, 0
End Function

;===============================================================================
; ENCIRCLEMENT COORDINATION
;===============================================================================
Function InitiateEngagement(squad.MTFFoxSquad)
	If squad = Null Then Return

	squad\engagementActive = True
	squad\encirclementPhase = 1  ; Spreading phase

	; Play tactical radio
	If TacticalRadioSFX(3) <> 0 Then
		PlaySound TacticalRadioSFX(3)
	EndIf

	; Assign roles if not assigned
	Local flankerLAssigned% = False
	Local flankerRAssigned% = False

	For i% = 0 To squad\memberCount - 1
		Local fox.MTFFoxState = squad\members[i]
		If fox = Null Then Continue

		If fox\role = FOX_ROLE_FLANKER_L Then flankerLAssigned = True
		If fox\role = FOX_ROLE_FLANKER_R Then flankerRAssigned = True
	Next

	; Auto-assign flankers if needed
	If Not flankerLAssigned Or Not flankerRAssigned Then
		Local assigned% = 0
		For i% = 0 To squad\memberCount - 1
			Local fox.MTFFoxState = squad\members[i]
			If fox = Null Then Continue
			If fox = squad\leader Then Continue

			If Not flankerLAssigned And assigned = 0 Then
				fox\role = FOX_ROLE_FLANKER_L
				flankerLAssigned = True
				assigned = assigned + 1
			ElseIf Not flankerRAssigned And assigned = 1 Then
				fox\role = FOX_ROLE_FLANKER_R
				flankerRAssigned = True
				assigned = assigned + 1
			EndIf
		Next
	EndIf

	; Set all members to alert state
	For i% = 0 To squad\memberCount - 1
		Local fox.MTFFoxState = squad\members[i]
		If fox <> Null Then
			fox\tacticalState = FOX_STATE_ALERT
			fox\stateTimer = 0.0
		EndIf
	Next
End Function

Function UpdateEncirclement(squad.MTFFoxSquad, closestDistSq#)
	If squad = Null Then Return

	; Check if encirclement is complete
	If squad\encirclementPhase = 2 Then
		; All flankers in position - signal close
		If TacticalRadioSFX(0) <> 0 Then
			PlaySound TacticalRadioSFX(0)
		EndIf
		squad\encirclementPhase = 3  ; Closing phase
	EndIf

	; Check if player is contained
	If closestDistSq < MTF_FOX_CLOSE_DISTANCE_SQ Then
		; Player surrounded
		squad\encirclementPhase = 4  ; Contained
	EndIf
End Function

Function SignalSquad(squad.MTFFoxSquad, spotter.MTFFoxState)
	If squad = Null Then Return

	; Alert all squad members
	For i% = 0 To squad\memberCount - 1
		Local fox.MTFFoxState = squad\members[i]
		If fox <> Null And fox <> spotter Then
			If fox\tacticalState = FOX_STATE_PATROL Then
				fox\tacticalState = FOX_STATE_ALERT
				fox\stateTimer = 0.0
			EndIf
		EndIf
	Next

	; Play flank call
	If TacticalRadioSFX(1) <> 0 Then
		PlaySound TacticalRadioSFX(1)
	EndIf
End Function

;===============================================================================
; FLASHBANG SYSTEM
;===============================================================================
Function ThrowFlashbang(fox.MTFFoxState)
	If fox = Null Then Return
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef

	; Play sound
	If FlashbangSFX <> 0 Then
		PlaySound FlashbangSFX
	EndIf

	; Calculate distance to player
	Local distSq# = GetDistanceSquaredToPlayer(n)

	; Check if player is within effect range
	If distSq <= FLASHBANG_RANGE_SQ Then
		; Full effect
		TriggerFlashbangEffect(1.0)
	ElseIf distSq <= FLASHBANG_RANGE_SQ * 2.0 Then
		; Partial effect
		Local falloff# = 1.0 - ((distSq - FLASHBANG_RANGE_SQ) / FLASHBANG_RANGE_SQ)
		TriggerFlashbangEffect(falloff)
	EndIf
End Function

Function TriggerFlashbangEffect(intensity#)
	FlashbangActive = True
	FlashbangIntensity = intensity
	FlashbangTimer = FLASHBANG_DURATION * intensity

	; Apply stun
	PlayerStunned = True
	PlayerStunTimer = FLASHBANG_STUN_DURATION * intensity

	; Disable player movement
	CanPlayerMove = False

	; Play ear ringing
	If FlashbangRingSFX <> 0 Then
		PlaySound FlashbangRingSFX
	EndIf
End Function

Function UpdateFlashbangEffect()
	If Not FlashbangActive Then Return

	FlashbangTimer = FlashbangTimer - FPSfactor

	If FlashbangTimer <= 0.0 Then
		FlashbangActive = False
		FlashbangIntensity = 0.0
		FlashbangTimer = 0.0
	Else
		; Decay intensity
		FlashbangIntensity = FlashbangTimer / FLASHBANG_DURATION
	EndIf
End Function

Function UpdatePlayerStun()
	If Not PlayerStunned Then Return

	PlayerStunTimer = PlayerStunTimer - FPSfactor

	If PlayerStunTimer <= 0.0 Then
		PlayerStunned = False
		PlayerStunTimer = 0.0

		; Re-enable movement if dialog not active
		If Not DialogActive Then
			CanPlayerMove = True
		EndIf
	EndIf
End Function

Function RenderFlashbangEffect()
	If Not FlashbangActive Then Return
	If FlashbangIntensity <= 0.0 Then Return

	Local gw% = GraphicsWidth()
	Local gh% = GraphicsHeight()

	; White flash overlay
	Local alpha% = Int(FlashbangIntensity * 255.0)

	; Draw white rectangle (Blitz3D doesn't have alpha rects)
	If FlashbangIntensity > 0.5 Then
		Color 255, 255, 255
		Rect 0, 0, gw, gh, True
	Else
		; Gradient fade effect with scan lines
		For y% = 0 To gh - 1 Step 2
			Local lineAlpha% = Int(FlashbangIntensity * 255.0 * (1.0 - Float(y) / Float(gh)))
			If lineAlpha > 128 Then
				Color 255, 255, 255
				Line 0, y, gw, y
			EndIf
		Next
	EndIf

	; Noise overlay during stun
	If PlayerStunned Then
		Local noiseCount% = Int(FlashbangIntensity * 1000.0)
		For i% = 0 To noiseCount
			Local nx% = Rand(0, gw - 1)
			Local ny% = Rand(0, gh - 1)
			Local nc% = Rand(200, 255)
			Color nc, nc, nc
			Plot nx, ny
		Next
	EndIf
End Function

;===============================================================================
; LINE OF SIGHT & SHADOW DETECTION
;===============================================================================
Function CheckLineOfSight%(sourceNPC.NPCs, targetEntity%)
	If sourceNPC = Null Or targetEntity = 0 Then Return False
	If sourceNPC\Collider = 0 Then Return False

	Local srcX# = EntityX(sourceNPC\Collider, True)
	Local srcY# = EntityY(sourceNPC\Collider, True) + 1.5  ; Eye level
	Local srcZ# = EntityZ(sourceNPC\Collider, True)

	Local tgtX# = EntityX(targetEntity, True)
	Local tgtY# = EntityY(targetEntity, True) + 0.8  ; Torso level
	Local tgtZ# = EntityZ(targetEntity, True)

	Local dx# = tgtX - srcX
	Local dy# = tgtY - srcY
	Local dz# = tgtZ - srcZ

	; Line pick to check for obstructions
	Local pick% = LinePick(srcX, srcY, srcZ, dx, dy, dz)

	If pick = 0 Then
		Return True  ; No obstruction
	EndIf

	; Check if picked entity is the player
	If PickedEntity() = targetEntity Then
		Return True
	EndIf

	; Check distance to pick vs distance to target
	Local pickDist# = Sqr(PickedX()*PickedX() + PickedY()*PickedY() + PickedZ()*PickedZ())
	Local targetDist# = Sqr(dx*dx + dy*dy + dz*dz)

	If pickDist >= targetDist - 0.5 Then
		Return True  ; Pick is beyond target
	EndIf

	Return False  ; Obstruction between NPC and player
End Function

Function CheckPlayerInShadow%()
	; Check if player is in a dark area
	; This is a simplified check - could be enhanced with actual light sampling

	If PlayerRoom = Null Then Return False

	; Check nearby lights
	Local lightCount% = 0
	Local totalLight# = 0.0

	; Sample room lights
	For i% = 0 To MaxRoomLights - 1
		If PlayerRoom\Lights[i] <> 0 Then
			Local lightDist# = EntityDistance(Collider, PlayerRoom\Lights[i])
			If lightDist < 10.0 Then
				totalLight = totalLight + (10.0 - lightDist) / 10.0
				lightCount = lightCount + 1
			EndIf
		EndIf
	Next

	If lightCount > 0 Then
		PlayerLightLevel = totalLight / Float(lightCount)
	Else
		PlayerLightLevel = 0.1  ; Ambient
	EndIf

	Return PlayerLightLevel < SHADOW_THRESHOLD
End Function

Function CheckPlayerInCover%()
	; Check if player has cover between them and nearest MTF
	; Simplified - checks for nearby entities that could provide cover

	PlayerInCover = False
	PlayerCoverEntity = 0

	; Find nearest MTF Fox
	Local nearestFox.MTFFoxState = Null
	Local nearestDistSq# = 100000.0

	For fox.MTFFoxState = Each MTFFoxState
		If fox\npcRef <> Null Then
			Local distSq# = GetDistanceSquaredToPlayer(fox\npcRef)
			If distSq < nearestDistSq Then
				nearestDistSq = distSq
				nearestFox = fox
			EndIf
		EndIf
	Next

	If nearestFox <> Null And nearestFox\npcRef <> Null Then
		; Check if LOS is blocked
		If Not nearestFox\hasLOS Then
			PlayerInCover = True
		EndIf
	EndIf

	Return PlayerInCover
End Function

;===============================================================================
; MOVEMENT HELPERS
;===============================================================================
Function MoveTowardTarget(fox.MTFFoxState)
	If fox = Null Then Return
	If fox\npcRef = Null Then Return

	Local n.NPCs = fox\npcRef

	; Calculate direction
	Local dx# = fox\targetX - EntityX(n\Collider, True)
	Local dz# = fox\targetZ - EntityZ(n\Collider, True)
	Local dist# = Sqr(dx*dx + dz*dz)

	If dist < 0.5 Then Return  ; Close enough

	; Normalize
	dx = dx / dist
	dz = dz / dist

	; Apply movement
	Local speed# = n\Speed * FPSfactor
	TranslateEntity n\Collider, dx * speed, 0, dz * speed

	; Face movement direction
	Local angle# = ATan2(dx, dz)
	RotateEntity n\Collider, 0, angle, 0
End Function

Function GetDistanceSquaredToPlayer#(n.NPCs)
	If n = Null Then Return 100000.0
	If n\Collider = 0 Then Return 100000.0
	If Collider = 0 Then Return 100000.0

	Local dx# = EntityX(Collider, True) - EntityX(n\Collider, True)
	Local dy# = EntityY(Collider, True) - EntityY(n\Collider, True)
	Local dz# = EntityZ(Collider, True) - EntityZ(n\Collider, True)

	Return dx*dx + dy*dy + dz*dz
End Function

;===============================================================================
; INTEGRATION WITH ORIGINAL MTF
;===============================================================================
Function IntegrateFoxTactics(n.NPCs)
	; Call from UpdateMTFUnit() for tactical override
	If Not FoxTacticsEnabled Then Return
	If CurrentDay < 3 Then Return

	Local fox.MTFFoxState = GetFoxState(n)
	If fox = Null Then
		; Not part of a Fox squad - could auto-add
		Return
	EndIf

	; Fox tactics override standard behavior
	; The tactical state machine handles movement
End Function

;===============================================================================
; SAVE/LOAD
;===============================================================================
Function SaveFoxTacticsState(file%)
	WriteInt file, FoxTacticsEnabled
	WriteInt file, FoxSquadCount

	For squad.MTFFoxSquad = Each MTFFoxSquad
		WriteInt file, squad\id
		WriteInt file, squad\memberCount
		WriteInt file, squad\alertLevel
		WriteInt file, squad\engagementActive
		WriteInt file, squad\encirclementPhase
		WriteFloat file, squad\playerLastKnownX
		WriteFloat file, squad\playerLastKnownY
		WriteFloat file, squad\playerLastKnownZ
	Next

	WriteInt file, FlashbangActive
	WriteFloat file, FlashbangIntensity
	WriteFloat file, FlashbangTimer
	WriteInt file, PlayerStunned
	WriteFloat file, PlayerStunTimer
End Function

Function LoadFoxTacticsState(file%)
	FoxTacticsEnabled = ReadInt(file)
	FoxSquadCount = ReadInt(file)

	For i% = 0 To FoxSquadCount - 1
		Local squad.MTFFoxSquad = CreateFoxSquad()
		squad\id = ReadInt(file)
		squad\memberCount = ReadInt(file)
		squad\alertLevel = ReadInt(file)
		squad\engagementActive = ReadInt(file)
		squad\encirclementPhase = ReadInt(file)
		squad\playerLastKnownX = ReadFloat(file)
		squad\playerLastKnownY = ReadFloat(file)
		squad\playerLastKnownZ = ReadFloat(file)

		; Note: Squad members need to be re-linked after NPC load
	Next

	FlashbangActive = ReadInt(file)
	FlashbangIntensity = ReadFloat(file)
	FlashbangTimer = ReadFloat(file)
	PlayerStunned = ReadInt(file)
	PlayerStunTimer = ReadFloat(file)
End Function

;===============================================================================
; CLEANUP
;===============================================================================
Function CleanupFoxTactics()
	; Clean up fox states
	For fox.MTFFoxState = Each MTFFoxState
		If fox\coverPoint <> 0 Then
			FreeEntity fox\coverPoint
		EndIf
		Delete fox
	Next

	; Clean up squads
	For squad.MTFFoxSquad = Each MTFFoxSquad
		Delete squad
	Next

	For i% = 0 To 3
		FoxSquads(i) = Null
	Next

	FoxSquadCount = 0

	; Free sounds
	If FlashbangSFX <> 0 Then FreeSound FlashbangSFX : FlashbangSFX = 0
	If FlashbangRingSFX <> 0 Then FreeSound FlashbangRingSFX : FlashbangRingSFX = 0

	For i% = 0 To 3
		If TacticalRadioSFX(i) <> 0 Then
			FreeSound TacticalRadioSFX(i)
			TacticalRadioSFX(i) = 0
		EndIf
	Next
End Function

;===============================================================================
; DEBUG
;===============================================================================
Function DebugFoxTactics()
	Color 255, 100, 100
	Text 10, 300, "=== MTF FOX TACTICS ==="
	Text 10, 315, "Squads: " + FoxSquadCount
	Text 10, 330, "Player Light: " + Int(PlayerLightLevel * 100) + "%"
	Text 10, 345, "Player In Cover: " + PlayerInCover
	Text 10, 360, "Flashbang: " + FlashbangActive + " (" + Int(FlashbangIntensity * 100) + "%)"
	Text 10, 375, "Stunned: " + PlayerStunned

	Local y% = 395
	For squad.MTFFoxSquad = Each MTFFoxSquad
		Text 10, y, "Squad " + squad\id + ": Alert=" + squad\alertLevel + " Phase=" + squad\encirclementPhase
		y = y + 15

		For i% = 0 To squad\memberCount - 1
			Local fox.MTFFoxState = squad\members[i]
			If fox <> Null Then
				Local stateName$ = GetFoxStateName(fox\tacticalState)
				Local roleName$ = GetFoxRoleName(fox\role)
				Text 20, y, roleName + ": " + stateName + " LOS=" + fox\hasLOS
				y = y + 12
			EndIf
		Next
	Next
End Function

Function GetFoxStateName$(state%)
	Select state
		Case FOX_STATE_PATROL : Return "PATROL"
		Case FOX_STATE_ALERT : Return "ALERT"
		Case FOX_STATE_FLANKING : Return "FLANK"
		Case FOX_STATE_ENGAGING : Return "ENGAGE"
		Case FOX_STATE_FLASHBANG : Return "FLASH"
		Case FOX_STATE_BREACH : Return "BREACH"
		Case FOX_STATE_CONTAIN : Return "CONTAIN"
	End Select
	Return "UNKNOWN"
End Function

Function GetFoxRoleName$(role%)
	Select role
		Case FOX_ROLE_LEADER : Return "LEAD"
		Case FOX_ROLE_POINTMAN : Return "POINT"
		Case FOX_ROLE_FLANKER_L : Return "FLNK-L"
		Case FOX_ROLE_FLANKER_R : Return "FLNK-R"
		Case FOX_ROLE_REAR : Return "REAR"
		Case FOX_ROLE_SUPPORT : Return "SUPP"
	End Select
	Return "UNK"
End Function
